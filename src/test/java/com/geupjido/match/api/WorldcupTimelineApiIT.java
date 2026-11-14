package com.geupjido.match.api;

import java.time.Instant;
import java.time.OffsetDateTime;
import java.time.ZoneOffset;
import java.util.Collections;
import java.util.List;
import java.util.UUID;

import jakarta.servlet.http.Cookie;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.condition.EnabledIfEnvironmentVariable;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.transaction.annotation.Transactional;

import com.geupjido.user.api.AnonymousVisitorCookie;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

@EnabledIfEnvironmentVariable(named = "RUN_POSTGIS_IT", matches = "true")
@SpringBootTest
@AutoConfigureMockMvc
@ActiveProfiles("local")
@Transactional
class WorldcupTimelineApiIT {

	@Autowired private MockMvc mockMvc;
	@Autowired private JdbcTemplate jdbcTemplate;

	@Test
	void 최근_선택을_본인_표시와_권역_필터로_조회한다() throws Exception {
		List<ComplexRow> leftComplexes = jdbcTemplate.query("""
			SELECT c.id, c.zone_id FROM complex c
			WHERE c.is_active = TRUE
			  AND c.zone_id = (SELECT zone_id FROM complex WHERE is_active = TRUE
			                   GROUP BY zone_id HAVING COUNT(*) >= 2
			                   ORDER BY zone_id LIMIT 1)
			ORDER BY c.id LIMIT 2
			""", (rs, rowNum) -> new ComplexRow(rs.getString("id"), rs.getString("zone_id")));
		assertThat(leftComplexes).hasSize(2);
		ComplexRow left = leftComplexes.getFirst();
		ComplexRow otherInZone = leftComplexes.get(1);
		ComplexRow right = jdbcTemplate.queryForObject("""
			SELECT id, zone_id FROM complex
			WHERE is_active = TRUE AND zone_id <> ? ORDER BY id LIMIT 1
			""", (rs, rowNum) -> new ComplexRow(rs.getString("id"),
				rs.getString("zone_id")), left.zoneId());
		assertThat(right).isNotNull();

		UUID otherVisitor = UUID.randomUUID();
		UUID ownVisitor = UUID.randomUUID();
		Instant firstAt = Instant.now().plusSeconds(120);
		Instant secondAt = firstAt.plusSeconds(1);
		long crossGame = insertGame(otherVisitor, left.zoneId());
		long withinGame = insertGame(ownVisitor, left.zoneId());
		insertVote(crossGame, left, right, left.id(), firstAt);
		insertVote(withinGame, left, otherInZone, otherInZone.id(), secondAt);

		String all = mockMvc.perform(get("/api/v1/worldcup/timeline")
				.cookie(new Cookie(AnonymousVisitorCookie.NAME, ownVisitor.toString())))
			.andExpect(status().isOk())
			.andExpect(jsonPath("$.items[0].displayName").value("나"))
			.andExpect(jsonPath("$.items[0].mine").value(true))
			.andExpect(jsonPath("$.items[0].selectedComplexId").value(otherInZone.id()))
			.andExpect(jsonPath("$.items[1].left.zoneId").value(left.zoneId()))
			.andExpect(jsonPath("$.items[1].right.zoneId").value(right.zoneId()))
			.andReturn().getResponse().getContentAsString();
		assertThat(all).doesNotContain(otherVisitor.toString(), ownVisitor.toString());

		mockMvc.perform(get("/api/v1/worldcup/timeline")
				.param("zoneId", right.zoneId()))
			.andExpect(status().isOk())
			.andExpect(jsonPath("$.items[0].selectedComplexId").value(left.id()))
			.andExpect(jsonPath("$.items[0].mine").value(false))
			.andExpect(jsonPath("$.items[0].displayName").value(
				org.hamcrest.Matchers.startsWith("익명의 ")));
	}

	private long insertGame(UUID visitor, String baseZoneId) {
		String matchup = "{\"left\":{\"name\":\"왼쪽 단지\"},"
			+ "\"right\":{\"name\":\"오른쪽 단지\"}}";
		String matchups = "[" + String.join(",", Collections.nCopies(10, matchup)) + "]";
		return jdbcTemplate.queryForObject("""
			INSERT INTO worldcup_game (anonymous_id, base_zone_id, tier_range_used,
			                           matchups, started_at)
			VALUES (?, ?, 0.2, CAST(? AS jsonb), ?)
			RETURNING id
			""", Long.class, visitor, baseZoneId, matchups,
			OffsetDateTime.ofInstant(Instant.now(), ZoneOffset.UTC));
	}

	private void insertVote(long gameId, ComplexRow left, ComplexRow right,
		String selectedComplexId, Instant selectedAt) {
		jdbcTemplate.update("""
			INSERT INTO worldcup_vote (game_id, round_number, left_complex_id,
			                           right_complex_id, selected_complex_id, selected_at,
			                           left_zone_id, right_zone_id)
			VALUES (?, 1, ?, ?, ?, ?, ?, ?)
			""", gameId, left.id(), right.id(), selectedComplexId,
			OffsetDateTime.ofInstant(selectedAt, ZoneOffset.UTC),
			left.zoneId(), right.zoneId());
	}

	private record ComplexRow(String id, String zoneId) {
	}
}
