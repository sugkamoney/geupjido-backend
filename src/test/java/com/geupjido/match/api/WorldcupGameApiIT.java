package com.geupjido.match.api;

import java.time.Instant;
import java.util.List;
import java.util.UUID;

import jakarta.servlet.http.Cookie;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.condition.EnabledIfEnvironmentVariable;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.http.HttpHeaders;
import org.springframework.http.MediaType;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.transaction.annotation.Transactional;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.geupjido.match.model.MatchZoneCandidate;
import com.geupjido.match.repository.MatchCandidateRepository;
import com.geupjido.match.repository.WorldcupGameRepository;
import com.geupjido.match.service.MatchGenerationService;
import com.geupjido.user.api.AnonymousVisitorCookie;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.put;
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
	private MatchGenerationService generator;

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
	void 미완료_게임은_시작_요청을_반복해도_기존_대진을_이어간다()
		throws Exception {
		UUID anonymousId = UUID.randomUUID();
		Cookie cookie = new Cookie(AnonymousVisitorCookie.NAME, anonymousId.toString());

		var first = mockMvc.perform(post("/api/v1/worldcup/games").cookie(cookie))
			.andExpect(status().isCreated())
			.andExpect(jsonPath("$.matchups.length()").value(10))
			.andExpect(jsonPath("$.nextRoundNumber").value(1))
			.andReturn().getResponse();
		var second = mockMvc.perform(post("/api/v1/worldcup/games").cookie(cookie))
			.andExpect(status().isOk())
			.andReturn().getResponse();
		assertThat(second.getHeader(HttpHeaders.SET_COOKIE)).isNull();
		assertThat(objectMapper.readTree(second.getContentAsString()))
			.isEqualTo(objectMapper.readTree(first.getContentAsString()));

		var current = mockMvc.perform(get("/api/v1/worldcup/games/current").cookie(cookie))
			.andExpect(status().isOk())
			.andReturn().getResponse();
		assertThat(objectMapper.readTree(current.getContentAsString()))
			.isEqualTo(objectMapper.readTree(second.getContentAsString()));
		assertThat(jdbcTemplate.queryForObject(
			"SELECT COUNT(*) FROM worldcup_game WHERE anonymous_id = ?",
			Integer.class,
			anonymousId
		)).isEqualTo(1);
	}

	@Test
	void 선택은_순서대로_한번만_저장되고_완료한_뒤에만_새_게임을_시작한다() throws Exception {
		UUID anonymousId = UUID.randomUUID();
		Cookie cookie = new Cookie(AnonymousVisitorCookie.NAME, anonymousId.toString());
		var started = mockMvc.perform(post("/api/v1/worldcup/games").cookie(cookie))
			.andExpect(status().isCreated())
			.andReturn().getResponse();
		JsonNode game = objectMapper.readTree(started.getContentAsString());
		String firstLeft = game.at("/matchups/0/left/complexId").asText();
		String firstRight = game.at("/matchups/0/right/complexId").asText();

		mockMvc.perform(vote(cookie, 2, game.at("/matchups/1/left/complexId").asText()))
			.andExpect(status().isConflict())
			.andExpect(jsonPath("$.code").value("VOTE_OUT_OF_ORDER"));
		mockMvc.perform(vote(cookie, 1, "not-a-candidate"))
			.andExpect(status().isBadRequest())
			.andExpect(jsonPath("$.code").value("INVALID_VOTE"));
		mockMvc.perform(vote(new Cookie(AnonymousVisitorCookie.NAME, UUID.randomUUID().toString()),
			1, firstLeft))
			.andExpect(status().isNotFound());

		mockMvc.perform(vote(cookie, 1, firstLeft))
			.andExpect(status().isOk())
			.andExpect(jsonPath("$.selections.length()").value(1))
			.andExpect(jsonPath("$.nextRoundNumber").value(2))
			.andExpect(jsonPath("$.completed").value(false));
		mockMvc.perform(vote(cookie, 1, firstLeft))
			.andExpect(status().isOk())
			.andExpect(jsonPath("$.selections.length()").value(1));
		mockMvc.perform(vote(cookie, 1, firstRight))
			.andExpect(status().isConflict())
			.andExpect(jsonPath("$.code").value("VOTE_ALREADY_RECORDED"));
		assertThat(jdbcTemplate.queryForObject(
			"SELECT COUNT(*) FROM worldcup_vote WHERE game_id = "
				+ "(SELECT MAX(id) FROM worldcup_game WHERE anonymous_id = ?)",
			Integer.class, anonymousId
		)).isEqualTo(1);

		mockMvc.perform(post("/api/v1/worldcup/games").cookie(cookie))
			.andExpect(status().isOk())
			.andExpect(jsonPath("$.nextRoundNumber").value(2));
		for (int round = 2; round <= 10; round++) {
			String selectedId = game.at("/matchups/" + (round - 1) + "/left/complexId").asText();
			mockMvc.perform(vote(cookie, round, selectedId))
				.andExpect(status().isOk())
				.andExpect(jsonPath("$.selections.length()").value(round));
		}
		mockMvc.perform(get("/api/v1/worldcup/games/current").cookie(cookie))
			.andExpect(status().isOk())
			.andExpect(jsonPath("$.completed").value(true))
			.andExpect(jsonPath("$.nextRoundNumber").doesNotExist());
		assertThat(jdbcTemplate.queryForObject(
			"SELECT COUNT(*) FROM worldcup_vote WHERE game_id = "
				+ "(SELECT MAX(id) FROM worldcup_game WHERE anonymous_id = ?)",
			Integer.class, anonymousId
		)).isEqualTo(10);
		mockMvc.perform(post("/api/v1/worldcup/games").cookie(cookie))
			.andExpect(status().isCreated())
			.andExpect(jsonPath("$.nextRoundNumber").value(1));
		assertThat(jdbcTemplate.queryForObject(
			"SELECT COUNT(*) FROM worldcup_game WHERE anonymous_id = ?",
			Integer.class, anonymousId
		)).isEqualTo(2);
	}

	private org.springframework.test.web.servlet.request.MockHttpServletRequestBuilder vote(
		Cookie cookie, int roundNumber, String selectedComplexId
	) throws Exception {
		return put("/api/v1/worldcup/games/current/rounds/{roundNumber}/vote", roundNumber)
			.cookie(cookie)
			.contentType(MediaType.APPLICATION_JSON)
			.content(objectMapper.writeValueAsString(new WorldcupVoteRequest(selectedComplexId)));
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
	void 하루_여섯_게임을_시작했다면_두_시작_API가_일곱번째를_거부한다() throws Exception {
		UUID anonymousId = UUID.randomUUID();
		var game = generator.generateForZone(candidateRepository.findActiveZones().getFirst().zoneId());
		for (int i = 0; i < 6; i++) {
			gameRepository.complete(gameRepository.save(anonymousId, game, Instant.now()).id());
		}
		Cookie cookie = new Cookie(AnonymousVisitorCookie.NAME, anonymousId.toString());
		mockMvc.perform(post("/api/v1/worldcup/games").cookie(cookie))
			.andExpect(status().isTooManyRequests())
			.andExpect(jsonPath("$.code").value("DAILY_GAME_LIMIT_REACHED"));
		mockMvc.perform(post("/api/v1/worldcup/zones/{zoneId}/games", game.baseZoneId())
				.cookie(cookie))
			.andExpect(status().isTooManyRequests())
			.andExpect(jsonPath("$.code").value("DAILY_GAME_LIMIT_REACHED"));
		assertThat(jdbcTemplate.queryForObject(
			"SELECT COUNT(*) FROM worldcup_game WHERE anonymous_id = ?",
			Integer.class, anonymousId
		)).isEqualTo(6);
	}

	@Test
	void Swagger_문서에_시작과_재조회_경로가_표시된다() throws Exception {
		mockMvc.perform(get("/v3/api-docs"))
			.andExpect(status().isOk())
			.andExpect(jsonPath("$.paths['/api/v1/worldcup/games'].post").exists())
			.andExpect(jsonPath("$.paths['/api/v1/worldcup/zones/{zoneId}/games'].post").exists())
			.andExpect(jsonPath("$.paths['/api/v1/worldcup/games/current'].get").exists());
		mockMvc.perform(get("/v3/api-docs"))
			.andExpect(jsonPath("$.paths['/api/v1/worldcup/games/current/rounds/{roundNumber}/vote'].put")
				.exists());
	}
}
