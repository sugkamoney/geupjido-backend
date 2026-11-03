package com.geupjido.match.api;

import java.util.List;
import java.util.UUID;

import jakarta.servlet.http.Cookie;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.condition.EnabledIfEnvironmentVariable;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.http.HttpHeaders;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.transaction.annotation.Transactional;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.geupjido.match.model.MatchZoneCandidate;
import com.geupjido.match.repository.MatchCandidateRepository;
import com.geupjido.match.repository.WorldcupGameRepository;
import com.geupjido.user.api.AnonymousVisitorCookie;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

@EnabledIfEnvironmentVariable(named = "RUN_POSTGIS_IT", matches = "true")
@SpringBootTest
@AutoConfigureMockMvc
@ActiveProfiles("local")
@Transactional
class WorldcupGameApiIT {

	@Autowired
	private MockMvc mockMvc;

	@Autowired
	private ObjectMapper objectMapper;

	@Autowired
	private MatchCandidateRepository candidateRepository;

	@Autowired
	private WorldcupGameRepository gameRepository;

	@Autowired
	private JdbcTemplate jdbcTemplate;

	@Test
	void 최초_방문은_쿠키를_받고_같은_대진을_재조회하지만_다른_방문자는_볼_수_없다()
		throws Exception {
		List<MatchZoneCandidate> zones = candidateRepository.findActiveZones();
		String zoneId = zones.getFirst().zoneId();

		var start = mockMvc.perform(post("/api/v1/worldcup/zones/{zoneId}/games", zoneId))
			.andExpect(status().isCreated())
			.andExpect(jsonPath("$.baseZoneId").value(zoneId))
			.andExpect(jsonPath("$.matchups.length()").value(10))
			.andExpect(jsonPath("$.matchups[0].roundNumber").value(1))
			.andExpect(jsonPath("$.matchups[0].left.complexId").isNotEmpty())
			.andExpect(jsonPath("$.matchups[0].left.name").isNotEmpty())
			.andExpect(jsonPath("$.matchups[0].left.representativeSale.priceTenThousandWon").isNumber())
			.andReturn().getResponse();
		String setCookie = start.getHeader(HttpHeaders.SET_COOKIE);
		assertThat(setCookie).contains("HttpOnly", "SameSite=Lax");
		assertThat(start.getHeader(HttpHeaders.LOCATION))
			.isEqualTo("/api/v1/worldcup/games/current");
		assertThat(start.getHeader(HttpHeaders.CACHE_CONTROL)).contains("no-store");

		UUID anonymousId = UUID.fromString(setCookie.split("[=;]")[1]);
		JsonNode created = objectMapper.readTree(start.getContentAsString());
		assertThat(gameRepository.findLatestByAnonymousId(anonymousId)).isPresent();

		var current = mockMvc.perform(get("/api/v1/worldcup/games/current")
				.cookie(new Cookie(AnonymousVisitorCookie.NAME, anonymousId.toString())))
			.andExpect(status().isOk())
			.andReturn().getResponse();
		assertThat(objectMapper.readTree(current.getContentAsString())).isEqualTo(created);
		assertThat(current.getHeader(HttpHeaders.CACHE_CONTROL)).contains("no-store");

		mockMvc.perform(get("/api/v1/worldcup/games/current")
				.cookie(new Cookie(AnonymousVisitorCookie.NAME, UUID.randomUUID().toString())))
			.andExpect(status().isNotFound())
			.andExpect(jsonPath("$.code").value("GAME_NOT_FOUND"));
	}

	@Test
	void 기존_쿠키로_새_게임을_시작하면_쿠키를_재발급하지_않고_최신_게임을_조회한다()
		throws Exception {
		UUID anonymousId = UUID.randomUUID();
		Cookie cookie = new Cookie(AnonymousVisitorCookie.NAME, anonymousId.toString());

		mockMvc.perform(post("/api/v1/worldcup/games").cookie(cookie))
			.andExpect(status().isCreated())
			.andExpect(jsonPath("$.matchups.length()").value(10));
		var second = mockMvc.perform(post("/api/v1/worldcup/games").cookie(cookie))
			.andExpect(status().isCreated())
			.andReturn().getResponse();
		assertThat(second.getHeader(HttpHeaders.SET_COOKIE)).isNull();

		var current = mockMvc.perform(get("/api/v1/worldcup/games/current").cookie(cookie))
			.andExpect(status().isOk())
			.andReturn().getResponse();
		assertThat(objectMapper.readTree(current.getContentAsString()))
			.isEqualTo(objectMapper.readTree(second.getContentAsString()));
		assertThat(jdbcTemplate.queryForObject(
			"SELECT COUNT(*) FROM worldcup_game WHERE anonymous_id = ?",
			Integer.class,
			anonymousId
		)).isEqualTo(2);
	}

	@Test
	void 후보가_없는_권역은_게임과_쿠키를_생성하지_않는다() throws Exception {
		var response = mockMvc.perform(post("/api/v1/worldcup/zones/{zoneId}/games", "missing-zone"))
			.andExpect(status().isConflict())
			.andExpect(jsonPath("$.code").value("GAME_UNAVAILABLE"))
			.andReturn().getResponse();

		assertThat(response.getHeader(HttpHeaders.SET_COOKIE)).isNull();
	}

	@Test
	void Swagger_문서에_시작과_재조회_경로가_표시된다() throws Exception {
		mockMvc.perform(get("/v3/api-docs"))
			.andExpect(status().isOk())
			.andExpect(jsonPath("$.paths['/api/v1/worldcup/games'].post").exists())
			.andExpect(jsonPath("$.paths['/api/v1/worldcup/zones/{zoneId}/games'].post").exists())
			.andExpect(jsonPath("$.paths['/api/v1/worldcup/games/current'].get").exists());
	}
}
