package com.geupjido.ranking.api;

import java.time.OffsetDateTime;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.condition.EnabledIfEnvironmentVariable;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.transaction.annotation.Transactional;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.header;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

@EnabledIfEnvironmentVariable(named = "RUN_POSTGIS_IT", matches = "true")
@SpringBootTest
@AutoConfigureMockMvc
@ActiveProfiles("local")
@Transactional
class RankingApiIT {
	@Autowired private MockMvc mockMvc;
	@Autowired private JdbcTemplate jdbcTemplate;
	@Autowired private ObjectMapper objectMapper;

	@Test
	void 지역마다_전체_순위와_급지_수를_계산하고_모으는_중은_겹쳐서_보인다()
		throws Exception {
		JsonNode all = body("/api/v1/rankings/zones?region=ALL&tab=GRADE_2");
		JsonNode seoul = body("/api/v1/rankings/zones?region=SEOUL&tab=GRADE_2");
		JsonNode other = body("/api/v1/rankings/zones?region=GYEONGGI_INCHEON&tab=GRADE_2");
		assertThat(all.path("totalZoneCount").asLong())
			.isEqualTo(seoul.path("totalZoneCount").asLong()
				+ other.path("totalZoneCount").asLong());
		assertThat(seoul.path("rankedZoneCount").asLong())
			.isLessThanOrEqualTo(seoul.path("totalZoneCount").asLong());
		assertThat(all.path("gradeCounts")).hasSize(5);
		assertThat(all.path("zones")).allSatisfy(zone -> {
			assertThat(zone.path("tier").asDouble()).isBetween(2.0, 2.9999);
			assertThat(zone.path("rankTotal").asLong())
				.isEqualTo(all.path("rankedZoneCount").asLong());
		});
		assertThat(seoul.path("zones")).allSatisfy(zone ->
			assertThat(zone.path("regionName").asText()).isEqualTo("서울"));

		JsonNode collecting = body("/api/v1/rankings/zones?tab=COLLECTING");
		assertThat(collecting.path("zones")).hasSize(
			collecting.path("collectingCount").asInt());
		assertThat(collecting.path("zones")).allSatisfy(zone ->
			assertThat(zone.path("rankingBasis").asText()).isEqualTo("INITIAL"));
		String grade2InitialId = null;
		for (JsonNode zone : all.path("zones")) {
			if (zone.path("rankingBasis").asText().equals("INITIAL")) {
				grade2InitialId = zone.path("zoneId").asText();
				break;
			}
		}
		assertThat(grade2InitialId).isNotNull();
		String overlappingId = grade2InitialId;
		assertThat(collecting.path("zones")).anySatisfy(zone ->
			assertThat(zone.path("zoneId").asText()).isEqualTo(overlappingId));
	}

	@Test
	void 직전_급지와_발행_상태를_조회하고_잘못된_요청을_거부한다() throws Exception {
		String zoneId = jdbcTemplate.queryForObject("""
			SELECT z.id FROM zone z JOIN city c ON c.id = z.city_id
			WHERE c.region_code = 'seoul' AND z.initial_tier BETWEEN 2.0 AND 2.9
			ORDER BY z.id LIMIT 1
			""", String.class);
		jdbcTemplate.update("""
			UPDATE zone
			SET ranking_basis = 'VOTE', tier = 2.4, score = 76.50,
			    previous_published_tier = 2.7
			WHERE id = ?
			""", zoneId);
		jdbcTemplate.update("""
			INSERT INTO worldcup_zone_vote_status
			(zone_id, valid_vote_count, participant_count, cross_zone_vote_count)
			VALUES (?, 80, 12, 25)
			ON CONFLICT (zone_id) DO UPDATE
			SET valid_vote_count = 80, participant_count = 12,
			    cross_zone_vote_count = 25
			""", zoneId);
		jdbcTemplate.update("""
			UPDATE worldcup_rating_publication
			SET last_completed_at = ?, previous_completed_at = ? WHERE id = 1
			""", OffsetDateTime.parse("2026-11-15T09:00:00Z"),
			OffsetDateTime.parse("2026-11-15T06:00:00Z"));

		JsonNode result = body("/api/v1/rankings/zones?region=SEOUL&tab=GRADE_2&sort=RISEN");
		JsonNode target = null;
		for (JsonNode zone : result.path("zones")) {
			if (zone.path("zoneId").asText().equals(zoneId)) {
				target = zone;
				break;
			}
		}
		assertThat(target).isNotNull();
		assertThat(target.path("movement").asText()).isEqualTo("UP");
		assertThat(target.path("tierDelta").decimalValue()).isEqualByComparingTo("0.3");
		assertThat(target.path("validVoteCount").asInt()).isEqualTo(80);
		assertThat(target.path("participantCount").asInt()).isEqualTo(12);
		assertThat(target.path("crossZoneVoteCount").asInt()).isEqualTo(25);
		assertThat(result.path("previousCompletedAt").isNull()).isFalse();

		mockMvc.perform(get("/api/v1/rankings/zones")
				.param("region", "UNKNOWN"))
			.andExpect(status().isBadRequest())
			.andExpect(jsonPath("$.code").value("INVALID_REQUEST"));
		mockMvc.perform(get("/api/v1/rankings/zones")
				.param("tab", "COLLECTING").param("sort", "RISEN"))
			.andExpect(status().isBadRequest())
			.andExpect(jsonPath("$.code").value("INVALID_REQUEST"));
		mockMvc.perform(get("/api/v1/rankings/zones")
				.header("Origin", "http://localhost:5173"))
			.andExpect(status().isOk())
			.andExpect(header().string("Access-Control-Allow-Origin", "http://localhost:5173"))
			.andExpect(header().string("Cache-Control", "no-store"));
	}

	private JsonNode body(String path) throws Exception {
		String content = mockMvc.perform(get(path))
			.andExpect(status().isOk())
			.andReturn().getResponse().getContentAsString();
		return objectMapper.readTree(content);
	}
}
