package com.geupjido.report;

import java.time.Clock;
import java.time.Instant;
import java.time.OffsetDateTime;
import java.time.ZoneId;
import java.time.ZoneOffset;
import java.util.UUID;
import java.util.concurrent.atomic.AtomicReference;

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

import com.geupjido.report.service.WeeklyReportPublicationService;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

@EnabledIfEnvironmentVariable(named = "RUN_POSTGIS_IT", matches = "true")
@SpringBootTest
@AutoConfigureMockMvc
@ActiveProfiles("local")
@Import(WeeklyReportPublicationIT.ClockConfig.class)
@Transactional
class WeeklyReportPublicationIT {
	private static final Instant CUTOFF = Instant.parse("2026-11-15T09:00:00Z");
	@Autowired private JdbcTemplate jdbcTemplate;
	@Autowired private WeeklyReportPublicationService service;
	@Autowired private MutableClock clock;
	@Autowired private MockMvc mockMvc;

	@Test
	void 민심_급지가_없으면_첫_리포트를_발행하지_않는다() throws Exception {
		clock.set(Instant.parse("2026-11-15T12:00:00Z"));
		assertThat(service.prepareLatestClosed()).isFalse();
		assertThat(service.publishLatestReady()).isFalse();
		mockMvc.perform(get("/api/v1/reports"))
			.andExpect(status().isOk())
			.andExpect(jsonPath("$.state").value("COLLECTING"))
			.andExpect(jsonPath("$.reports.length()").value(0));
	}

	@Test
	void 일시적_실패_뒤_정상_집계에서_표가_부족하면_수집중으로_복구한다() throws Exception {
		clock.set(Instant.parse("2026-11-15T12:01:00Z"));
		jdbcTemplate.update("""
			INSERT INTO weekly_report_attempt_failure (period_end_at, failed_at)
			VALUES (?, ?)
			""", utc(CUTOFF), utc(CUTOFF.plusSeconds(60)));
		mockMvc.perform(get("/api/v1/reports"))
			.andExpect(jsonPath("$.state").value("DELAYED"));

		assertThat(service.prepareLatestClosed()).isFalse();

		mockMvc.perform(get("/api/v1/reports"))
			.andExpect(jsonPath("$.state").value("COLLECTING"))
			.andExpect(jsonPath("$.reports.length()").value(0));
		assertThat(jdbcTemplate.queryForObject("""
			SELECT count(*) FROM weekly_report_attempt_failure WHERE period_end_at = ?
			""", Integer.class, utc(CUTOFF))).isZero();
	}

	@Test
	void 유효표_기준으로_초안을_만들고_21시부터_한번만_공개한다() throws Exception {
		clock.set(CUTOFF.plusSeconds(60));
		seedZonesAndVotes();
		assertThat(service.prepareLatestClosed()).isTrue();
		assertThat(service.prepareLatestClosed()).isFalse();
		assertThat(service.publishLatestReady()).isFalse();
		assertThat(jdbcTemplate.queryForObject("""
			SELECT period_valid_vote_count FROM weekly_report
			WHERE period_end_at = ? AND published_at IS NULL
			""", Long.class, utc(CUTOFF))).isEqualTo(60);
		assertThat(jdbcTemplate.queryForObject("""
			SELECT period_participant_count FROM weekly_report WHERE period_end_at = ?
			""", Long.class, utc(CUTOFF))).isEqualTo(10);
		assertThat(jdbcTemplate.queryForObject("""
			SELECT count(*) FROM weekly_report_zone_snapshot
			WHERE ranking_basis = 'VOTE' AND valid_vote_count = 50
			  AND participant_count = 10 AND cross_zone_vote_count = 40
			""", Integer.class)).isEqualTo(2);
		assertThat(jdbcTemplate.queryForObject("""
			SELECT initial_tier FROM weekly_report_zone_snapshot
			WHERE zone_id = 'report-zone-a'
			""", java.math.BigDecimal.class))
			.isEqualByComparingTo("2.3");
		mockMvc.perform(get("/api/v1/reports"))
			.andExpect(status().isOk())
			.andExpect(jsonPath("$.state").value("PREPARING"))
			.andExpect(jsonPath("$.reports.length()").value(0));

		clock.set(Instant.parse("2026-11-15T12:00:00Z"));
		assertThat(service.publishLatestReady()).isTrue();
		assertThat(service.publishLatestReady()).isFalse();
		Long id = jdbcTemplate.queryForObject(
			"SELECT id FROM weekly_report WHERE period_end_at = ?", Long.class, utc(CUTOFF));
		mockMvc.perform(get("/api/v1/reports"))
			.andExpect(status().isOk())
			.andExpect(jsonPath("$.state").value("PUBLISHED"))
			.andExpect(jsonPath("$.reports[0].reportId").value(id));
		mockMvc.perform(get("/api/v1/reports/{reportId}", id))
			.andExpect(status().isOk())
			.andExpect(jsonPath("$.validVoteCount").value(60))
			.andExpect(jsonPath("$.participantCount").value(10))
			.andExpect(jsonPath("$.summary").value(org.hamcrest.Matchers.startsWith(
				"첫 민심 급지 리포트예요.")))
			.andExpect(jsonPath("$.changedGradeZoneCount").doesNotExist())
			.andExpect(jsonPath("$.risenZones.length()").value(0))
			.andExpect(jsonPath("$.nearBoundaryZones.length()").value(1));
	}

	private void seedZonesAndVotes() {
		jdbcTemplate.update("INSERT INTO region (code, name, display_order) VALUES ('report-it', '리포트', 99)");
		jdbcTemplate.update("""
			INSERT INTO city (id, region_code, name, polygon, center)
			VALUES ('reportcity', 'report-it', '테스트 구',
			        ST_GeomFromText('MULTIPOLYGON(((0 0, 2 0, 2 1, 0 1, 0 0)))', 4326),
			        ST_GeomFromText('POINT(1 0.5)', 4326))
			""");
		jdbcTemplate.update("""
			INSERT INTO zone (id, city_id, name, dong_codes, polygon, center, initial_tier)
			VALUES ('report-zone-a', 'reportcity', '첫 권역', ARRAY['a'],
			        ST_GeomFromText('MULTIPOLYGON(((0 0, 1 0, 1 1, 0 1, 0 0)))', 4326),
			        ST_GeomFromText('POINT(0.5 0.5)', 4326), 2.3),
			       ('report-zone-b', 'reportcity', '둘째 권역', ARRAY['b'],
			        ST_GeomFromText('MULTIPOLYGON(((1 0, 2 0, 2 1, 1 1, 1 0)))', 4326),
			        ST_GeomFromText('POINT(1.5 0.5)', 4326), 3.1)
			""");
		jdbcTemplate.update("""
			INSERT INTO complex (id, zone_id, name, address_jibun, households, vote_initial_rating)
			VALUES ('report-a1', 'report-zone-a', 'A1', '테스트', 100, -2.3),
			       ('report-a2', 'report-zone-a', 'A2', '테스트', 100, -2.3),
			       ('report-b1', 'report-zone-b', 'B1', '테스트', 100, -3.1),
			       ('report-b2', 'report-zone-b', 'B2', '테스트', 100, -3.1)
			""");
		String[][] pairs = {
			{"report-a1", "report-b1", "report-zone-a", "report-zone-b"},
			{"report-a1", "report-b2", "report-zone-a", "report-zone-b"},
			{"report-a2", "report-b1", "report-zone-a", "report-zone-b"},
			{"report-a2", "report-b2", "report-zone-a", "report-zone-b"},
			{"report-a1", "report-a2", "report-zone-a", "report-zone-a"},
			{"report-b1", "report-b2", "report-zone-b", "report-zone-b"}
		};
		for (int visitor = 0; visitor < 10; visitor++) {
			Long gameId = jdbcTemplate.queryForObject("""
				INSERT INTO worldcup_game
				(anonymous_id, base_zone_id, tier_range_used, matchups, started_at)
				VALUES (?, 'report-zone-a', 0.2,
				        '[{}, {}, {}, {}, {}, {}, {}, {}, {}, {}]'::jsonb, ?)
				RETURNING id
				""", Long.class, UUID.randomUUID(), utc(CUTOFF.minusSeconds(7200)));
			for (int round = 0; round < pairs.length; round++) {
				String[] pair = pairs[round];
				jdbcTemplate.update("""
					INSERT INTO worldcup_vote
					(game_id, round_number, left_complex_id, right_complex_id,
					 selected_complex_id, left_zone_id, right_zone_id, selected_at)
					VALUES (?, ?, ?, ?, ?, ?, ?, ?)
					""", gameId, round + 1, pair[0], pair[1], pair[0],
					pair[2], pair[3], utc(CUTOFF.minusSeconds(3600 - round)));
			}
		}
	}

	private static OffsetDateTime utc(Instant instant) {
		return OffsetDateTime.ofInstant(instant, ZoneOffset.UTC);
	}

	@TestConfiguration
	static class ClockConfig {
		@Bean
		@Primary
		MutableClock reportTestClock() {
			return new MutableClock();
		}
	}

	static final class MutableClock extends Clock {
		private final AtomicReference<Instant> now = new AtomicReference<>(CUTOFF);

		void set(Instant instant) {
			now.set(instant);
		}

		@Override
		public ZoneId getZone() {
			return ZoneOffset.UTC;
		}

		@Override
		public Clock withZone(ZoneId zone) {
			return Clock.fixed(now.get(), zone);
		}

		@Override
		public Instant instant() {
			return now.get();
		}
	}
}
