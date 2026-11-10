package com.geupjido.match.api;

import jakarta.servlet.http.Cookie;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.condition.EnabledIfEnvironmentVariable;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.http.HttpHeaders;
import org.springframework.http.MediaType;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.transaction.annotation.Transactional;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.geupjido.user.api.AnonymousVisitorCookie;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.options;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.put;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.header;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

@EnabledIfEnvironmentVariable(named = "RUN_POSTGIS_IT", matches = "true")
@SpringBootTest
@AutoConfigureMockMvc
@ActiveProfiles("local")
@Transactional
class WorldcupBrowserIntegrationIT {

	private static final String FRONTEND_ORIGIN = "http://localhost:5173";

	@Autowired private MockMvc mockMvc;
	@Autowired private ObjectMapper objectMapper;

	@Test
	void 허용한_출처의_사전_요청은_쿠키를_포함한_선택_요청을_허용한다() throws Exception {
		mockMvc.perform(options("/api/v1/worldcup/games/current/rounds/1/vote")
				.header(HttpHeaders.ORIGIN, FRONTEND_ORIGIN)
				.header(HttpHeaders.ACCESS_CONTROL_REQUEST_METHOD, "PUT")
				.header(HttpHeaders.ACCESS_CONTROL_REQUEST_HEADERS, "content-type"))
			.andExpect(status().isOk())
			.andExpect(header().string(HttpHeaders.ACCESS_CONTROL_ALLOW_ORIGIN, FRONTEND_ORIGIN))
			.andExpect(header().string(HttpHeaders.ACCESS_CONTROL_ALLOW_CREDENTIALS, "true"))
			.andExpect(header().string(HttpHeaders.ACCESS_CONTROL_ALLOW_METHODS, "GET,POST,PUT"))
			.andExpect(header().string(HttpHeaders.ACCESS_CONTROL_ALLOW_HEADERS, "content-type"));
	}

	@Test
	void 허용하지_않은_출처의_월드컵_요청은_거부한다() throws Exception {
		mockMvc.perform(options("/api/v1/worldcup/games/current/rounds/1/vote")
				.header(HttpHeaders.ORIGIN, "https://untrusted.example")
				.header(HttpHeaders.ACCESS_CONTROL_REQUEST_METHOD, "PUT"))
			.andExpect(status().isForbidden())
			.andExpect(header().doesNotExist(HttpHeaders.ACCESS_CONTROL_ALLOW_ORIGIN))
			.andExpect(header().doesNotExist(HttpHeaders.ACCESS_CONTROL_ALLOW_CREDENTIALS));

		mockMvc.perform(get("/api/v1/worldcup/zones")
				.header(HttpHeaders.ORIGIN, "https://untrusted.example"))
			.andExpect(status().isForbidden());
	}

	@Test
	void 권역_선택부터_열_판_완료까지_같은_쿠키로_이어간다() throws Exception {
		String zonesJson = mockMvc.perform(get("/api/v1/worldcup/zones")
				.header(HttpHeaders.ORIGIN, FRONTEND_ORIGIN))
			.andExpect(status().isOk())
			.andExpect(header().string(HttpHeaders.ACCESS_CONTROL_ALLOW_ORIGIN, FRONTEND_ORIGIN))
			.andExpect(header().string(HttpHeaders.ACCESS_CONTROL_ALLOW_CREDENTIALS, "true"))
			.andReturn().getResponse().getContentAsString();
		String zoneId = null;
		for (JsonNode zone : objectMapper.readTree(zonesJson)) {
			if (zone.path("canStart").asBoolean()) {
				zoneId = zone.path("zoneId").asText();
				break;
			}
		}
		assertThat(zoneId).isNotNull();

		mockMvc.perform(get("/api/v1/worldcup/zones/{zoneId}/alternatives", zoneId)
				.header(HttpHeaders.ORIGIN, FRONTEND_ORIGIN))
			.andExpect(status().isOk());
		var started = mockMvc.perform(post("/api/v1/worldcup/zones/{zoneId}/games", zoneId)
				.header(HttpHeaders.ORIGIN, FRONTEND_ORIGIN))
			.andExpect(status().isCreated())
			.andExpect(header().string(HttpHeaders.ACCESS_CONTROL_ALLOW_ORIGIN, FRONTEND_ORIGIN))
			.andExpect(header().string(HttpHeaders.ACCESS_CONTROL_ALLOW_CREDENTIALS, "true"))
			.andExpect(jsonPath("$.matchups.length()").value(10))
			.andReturn().getResponse();
		String setCookie = started.getHeader(HttpHeaders.SET_COOKIE);
		assertThat(setCookie).contains("gjd_uid=", "HttpOnly", "SameSite=Lax");
		Cookie cookie = new Cookie(AnonymousVisitorCookie.NAME, setCookie.split("[=;]")[1]);
		JsonNode game = objectMapper.readTree(started.getContentAsString());

		String firstComplexId = game.at("/matchups/0/left/complexId").asText();
		mockMvc.perform(put("/api/v1/worldcup/games/current/rounds/1/vote")
				.header(HttpHeaders.ORIGIN, FRONTEND_ORIGIN)
				.cookie(cookie)
				.contentType(MediaType.APPLICATION_JSON)
				.content(objectMapper.writeValueAsString(new WorldcupVoteRequest(firstComplexId))))
			.andExpect(status().isOk())
			.andExpect(jsonPath("$.nextRoundNumber").value(2))
			.andExpect(jsonPath("$.roundResult.roundNumber").value(1));
		mockMvc.perform(put("/api/v1/worldcup/games/current/rounds/2/skip")
				.header(HttpHeaders.ORIGIN, FRONTEND_ORIGIN).cookie(cookie))
			.andExpect(status().isOk())
			.andExpect(jsonPath("$.nextRoundNumber").value(3));

		mockMvc.perform(get("/api/v1/worldcup/games/current")
				.header(HttpHeaders.ORIGIN, FRONTEND_ORIGIN).cookie(cookie))
			.andExpect(status().isOk())
			.andExpect(jsonPath("$.selections.length()").value(1))
			.andExpect(jsonPath("$.skips.length()").value(1))
			.andExpect(jsonPath("$.nextRoundNumber").value(3));

		for (int round = 3; round <= 10; round++) {
			var request = put("/api/v1/worldcup/games/current/rounds/{roundNumber}/"
				+ (round % 2 == 0 ? "skip" : "vote"), round)
				.header(HttpHeaders.ORIGIN, FRONTEND_ORIGIN).cookie(cookie);
			if (round % 2 == 1) {
				String complexId = game.at("/matchups/" + (round - 1) + "/left/complexId").asText();
				request.contentType(MediaType.APPLICATION_JSON)
					.content(objectMapper.writeValueAsString(new WorldcupVoteRequest(complexId)));
			}
			mockMvc.perform(request).andExpect(status().isOk());
		}
		mockMvc.perform(get("/api/v1/worldcup/games/current")
				.header(HttpHeaders.ORIGIN, FRONTEND_ORIGIN).cookie(cookie))
			.andExpect(status().isOk())
			.andExpect(jsonPath("$.completed").value(true))
			.andExpect(jsonPath("$.nextRoundNumber").doesNotExist())
			.andExpect(jsonPath("$.selections.length()").value(5))
			.andExpect(jsonPath("$.skips.length()").value(5));
		mockMvc.perform(get("/api/v1/worldcup/zones/{zoneId}/ranking", zoneId)
				.header(HttpHeaders.ORIGIN, FRONTEND_ORIGIN))
			.andExpect(status().isOk())
			.andExpect(jsonPath("$.zoneId").value(zoneId));
	}
}
