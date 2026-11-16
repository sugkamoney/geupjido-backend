package com.geupjido.report;

import java.time.Instant;
import java.time.OffsetDateTime;
import java.time.ZoneOffset;
import java.time.Clock;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.condition.EnabledIfEnvironmentVariable;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.test.context.TestConfiguration;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Import;
import org.springframework.context.annotation.Primary;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.transaction.annotation.Transactional;

import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.header;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

@EnabledIfEnvironmentVariable(named = "RUN_POSTGIS_IT", matches = "true")
@SpringBootTest
@AutoConfigureMockMvc
@ActiveProfiles("local")
@Import(WeeklyReportQueryApiIT.ClockConfig.class)
@Transactional
class WeeklyReportQueryApiIT {
	@Autowired private JdbcTemplate jdbcTemplate;
	@Autowired private MockMvc mockMvc;

	@Test
	void 첫_리포트_전에는_마지막_집계의_수집중_권역을_제공한다() throws Exception {
		jdbcTemplate.update("INSERT INTO region (code, name, display_order) VALUES ('report-progress', '서울', 99)");
		jdbcTemplate.update("""
			INSERT INTO city (id, region_code, name, polygon, center)
			VALUES ('reportpro', 'report-progress', '테스트 구',
			 ST_GeomFromText('MULTIPOLYGON(((0 0, 1 0, 1 1, 0 1, 0 0)))', 4326),
			 ST_GeomFromText('POINT(0.5 0.5)', 4326))
			""");
		jdbcTemplate.update("""
			INSERT INTO zone (id, city_id, name, dong_codes, polygon, center, initial_tier)
			VALUES ('report-progress-zone', 'reportpro', '수집 권역', ARRAY['a'],
			 ST_GeomFromText('MULTIPOLYGON(((0 0, 1 0, 1 1, 0 1, 0 0)))', 4326),
			 ST_GeomFromText('POINT(0.5 0.5)', 4326), 3.2)
			""");
		jdbcTemplate.update("""
			INSERT INTO worldcup_zone_vote_status
			(zone_id, valid_vote_count, participant_count, cross_zone_vote_count)
			VALUES ('report-progress-zone', 31, 7, 12)
			""");
		mockMvc.perform(get("/api/v1/reports"))
			.andExpect(status().isOk())
			.andExpect(jsonPath("$.state").value("COLLECTING"))
			.andExpect(jsonPath("$.reports.length()").value(0))
			.andExpect(jsonPath("$.collectingZones[0].zoneId")
				.value("report-progress-zone"))
			.andExpect(jsonPath("$.collectingZones[0].validVoteCount").value(31))
			.andExpect(jsonPath("$.collectingZones[0].requiredValidVoteCount").value(50));
	}

	@Test
	void 첫_리포트_준비_실패를_수집중과_구분한다() throws Exception {
		jdbcTemplate.update("""
			INSERT INTO weekly_report_attempt_failure (period_end_at, failed_at)
			VALUES (?, ?)
			""", utc(Instant.parse("2026-11-22T09:00:00Z")),
			utc(Instant.parse("2026-11-22T09:05:00Z")));
		mockMvc.perform(get("/api/v1/reports"))
			.andExpect(status().isOk())
			.andExpect(jsonPath("$.state").value("DELAYED"))
			.andExpect(jsonPath("$.expectedReleaseAt").value("2026-11-22T12:00:00Z"))
			.andExpect(jsonPath("$.reports.length()").value(0));
	}

	@Test
	void 두_발행본의_공통_민심_권역만_비교하고_초안은_숨긴다() throws Exception {
		long first = report("2026-11-01T09:00:00Z", "2026-11-08T09:00:00Z", null);
		zone(first, "a", "가 권역", "city1", "가 구", "VOTE", "2.0", 50, 10, 20);
		zone(first, "b", "나 권역", "city1", "가 구", "VOTE", "3.5", 55, 11, 22);
		zone(first, "c", "다 권역", "city2", "나 구", "INITIAL", null, 45, 8, 15);
		publish(first, "2026-11-08T12:00:00Z");

		long second = report("2026-11-08T09:00:00Z", "2026-11-15T09:00:00Z", 1);
		zone(second, "a", "가 권역", "city1", "가 구", "VOTE", "1.9", 60, 12, 24);
		zone(second, "b", "나 권역", "city1", "가 구", "VOTE", "3.7", 65, 13, 26);
		zone(second, "c", "다 권역", "city2", "나 구", "INITIAL", null, 49, 9, 19);
		zone(second, "d", "라 권역", "city2", "나 구", "VOTE", "4.0", 52, 10, 20);
		publish(second, "2026-11-15T12:00:00Z");

		long draft = report("2026-11-15T09:00:00Z", "2026-11-22T09:00:00Z", null);
		mockMvc.perform(get("/api/v1/reports"))
			.andExpect(status().isOk())
			.andExpect(header().string("Cache-Control", "no-store"))
			.andExpect(jsonPath("$.state").value("DELAYED"))
			.andExpect(jsonPath("$.expectedReleaseAt").value("2026-11-22T12:00:00Z"))
			.andExpect(jsonPath("$.reports.length()").value(2))
			.andExpect(jsonPath("$.reports[0].reportId").value(second))
			.andExpect(jsonPath("$.reports[0].latest").value(true))
			.andExpect(jsonPath("$.reports[1].reportId").value(first))
			.andExpect(jsonPath("$.collectingZones.length()").value(0));
		mockMvc.perform(get("/api/v1/reports/{reportId}", second))
			.andExpect(status().isOk())
			.andExpect(jsonPath("$.previousReportId").value(first))
			.andExpect(jsonPath("$.changedGradeZoneCount").value(1))
			.andExpect(jsonPath("$.risenZones[0].zoneId").value("a"))
			.andExpect(jsonPath("$.risenZones[0].tierDelta").value(0.1))
			.andExpect(jsonPath("$.fallenZones[0].zoneId").value("b"))
			.andExpect(jsonPath("$.nearBoundaryZones[0].zoneId").value("d"))
			.andExpect(jsonPath("$.collectingZones[0].zoneId").value("c"))
			.andExpect(jsonPath("$.collectingZones[0].requiredValidVoteCount").value(50))
			.andExpect(jsonPath("$.cityChanges.length()").value(1))
			.andExpect(jsonPath("$.cityChanges[0].cityId").value("city1"))
			.andExpect(jsonPath("$.cityChanges[0].comparedZoneCount").value(2));
		mockMvc.perform(get("/api/v1/reports/{reportId}", first))
			.andExpect(status().isOk())
			.andExpect(jsonPath("$.nextReportId").value(second))
			.andExpect(jsonPath("$.risenZones.length()").value(0));
		mockMvc.perform(get("/api/v1/reports/{reportId}", draft))
			.andExpect(status().isNotFound())
			.andExpect(jsonPath("$.code").value("REPORT_NOT_FOUND"));
		mockMvc.perform(get("/v3/api-docs"))
			.andExpect(status().isOk())
			.andExpect(jsonPath("$.paths['/api/v1/reports'].get.tags[0]").value("리포트"))
			.andExpect(jsonPath("$.paths['/api/v1/reports/{reportId}'].get.tags[0]")
				.value("리포트"));
	}

	private long report(String start, String end, Integer changed) {
		Instant endAt = Instant.parse(end);
		Long id = jdbcTemplate.queryForObject("""
			INSERT INTO weekly_report
			(period_start_at, period_end_at, cutoff_completed_at,
			 period_valid_vote_count, period_participant_count,
			 changed_grade_zone_count, summary_text)
			VALUES (?, ?, ?, 60, 10, ?, '테스트 요약') RETURNING id
			""", Long.class, utc(Instant.parse(start)), utc(endAt),
			utc(endAt.plusSeconds(60)), changed);
		return java.util.Objects.requireNonNull(id);
	}

	private void publish(long reportId, String publishedAt) {
		jdbcTemplate.update("UPDATE weekly_report SET published_at = ? WHERE id = ?",
			utc(Instant.parse(publishedAt)), reportId);
	}

	private void zone(long reportId, String id, String name, String cityId,
		String cityName, String basis, String tier, int votes, int people, int cross) {
		jdbcTemplate.update("""
			INSERT INTO weekly_report_zone_snapshot
			(report_id, zone_id, zone_name, city_id, city_name, region_code,
			 region_name, ranking_basis, vote_tier, valid_vote_count,
			 participant_count, cross_zone_vote_count)
			VALUES (?, ?, ?, ?, ?, 'seoul', '서울', ?, ?, ?, ?, ?)
			""", reportId, id, name, cityId, cityName, basis,
			tier == null ? null : new java.math.BigDecimal(tier), votes, people, cross);
	}

	private static OffsetDateTime utc(Instant instant) {
		return OffsetDateTime.ofInstant(instant, ZoneOffset.UTC);
	}

	@TestConfiguration
	static class ClockConfig {
		@Bean
		@Primary
		Clock reportQueryTestClock() {
			return Clock.fixed(Instant.parse("2026-11-22T12:01:00Z"), ZoneOffset.UTC);
		}
	}
}
