package com.geupjido.report;

import java.nio.charset.StandardCharsets;
import java.time.Clock;
import java.time.Instant;
import java.time.OffsetDateTime;
import java.time.ZoneId;
import java.time.ZoneOffset;
import java.util.ArrayList;
import java.util.List;
import java.util.UUID;
import java.util.concurrent.atomic.AtomicReference;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.node.ObjectNode;
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

import com.geupjido.report.service.WeeklyReportPublicationService;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

@EnabledIfEnvironmentVariable(named = "RUN_POSTGIS_IT", matches = "true")
@SpringBootTest
@AutoConfigureMockMvc
@ActiveProfiles("local")
@Import(WeeklyReportMonthSimulationIT.ClockConfig.class)
class WeeklyReportMonthSimulationIT {
	private static final String CLONE_DB = "geupjido_weekly_report_month_smoke";
	private static final Instant FIRST_CUTOFF = Instant.parse("2026-11-08T09:00:00Z");
	private static final long WEEK_SECONDS = 7L * 24 * 60 * 60;
	private static final String MATCHUPS = "[{}, {}, {}, {}, {}, {}, {}, {}, {}, {}]";
	private static final int[] CHANGED_GRADES = {0, 2, 3, 0};
	private static final String[][] EXPECTED_RISES = {
		{}, {"sim-a", "sim-c"}, {"sim-d", "sim-b"}, {"sim-b"}
	};
	private static final String[][] EXPECTED_FALLS = {
		{}, {"sim-d", "sim-b"}, {"sim-c", "sim-a"}, {"sim-c", "sim-d"}
	};
	private static final String[][] PAIRS = {
		{"sim-a1", "sim-b1", "sim-a", "sim-b"},
		{"sim-a2", "sim-b2", "sim-a", "sim-b"},
		{"sim-a1", "sim-c1", "sim-a", "sim-c"},
		{"sim-a2", "sim-c2", "sim-a", "sim-c"},
		{"sim-a1", "sim-d1", "sim-a", "sim-d"},
		{"sim-b1", "sim-c1", "sim-b", "sim-c"},
		{"sim-b1", "sim-d1", "sim-b", "sim-d"},
		{"sim-b2", "sim-d2", "sim-b", "sim-d"},
		{"sim-c1", "sim-d1", "sim-c", "sim-d"},
		{"sim-c2", "sim-d2", "sim-c", "sim-d"}
	};

	@Autowired private JdbcTemplate jdbcTemplate;
	@Autowired private WeeklyReportPublicationService publicationService;
	@Autowired private MutableClock clock;
	@Autowired private MockMvc mockMvc;
	@Autowired private ObjectMapper objectMapper;

	@Test
	void 한_달치_가상_투표를_누적해_네_주간_리포트를_발행하고_API를_검증한다() throws Exception {
		requireCloneDatabase();
		resetClonedVotesAndReports();
		seedZonesAndComplexes();
		clock.set(FIRST_CUTOFF.minusSeconds(3600));
		JsonNode beforeFirstVote = list();
		assertThat(beforeFirstVote.path("state").asText()).isEqualTo("COLLECTING");
		assertThat(beforeFirstVote.path("reports").size()).isZero();
		assertThat(beforeFirstVote.path("collectingZones").size()).isZero();
		List<Long> reportIds = new ArrayList<>();
		List<JsonNode> publishedDetails = new ArrayList<>();

		for (int week = 0; week < 4; week++) {
			Instant cutoff = FIRST_CUTOFF.plusSeconds(week * WEEK_SECONDS);
			seedWeek(week, cutoff);
			if (week == 0) {
				seedBoundaryVote(cutoff);
			}
			clock.set(cutoff.plusSeconds(60));
			assertThat(publicationService.prepareLatestClosed()).isTrue();
			assertThat(publicationService.prepareLatestClosed()).isFalse();
			assertThat(publicationService.publishLatestReady()).isFalse();
			long draftId = jdbcTemplate.queryForObject(
				"SELECT id FROM weekly_report WHERE period_end_at = ?", Long.class, utc(cutoff));
			JsonNode preparing = list();
			assertThat(preparing.path("state").asText()).isEqualTo("PREPARING");
			assertThat(preparing.path("reports").size()).isEqualTo(week);
			mockMvc.perform(get("/api/v1/reports/{reportId}", draftId))
				.andExpect(status().isNotFound())
				.andExpect(jsonPath("$.code").value("REPORT_NOT_FOUND"));

			clock.set(cutoff.plusSeconds(3 * 3600));
			assertThat(publicationService.publishLatestReady()).isTrue();
			assertThat(publicationService.publishLatestReady()).isFalse();
			reportIds.add(draftId);
			JsonNode listing = list();
			assertThat(listing.path("state").asText()).isEqualTo("PUBLISHED");
			assertThat(listing.path("reports").size()).isEqualTo(week + 1);
			assertThat(listing.path("reports").get(0).path("reportId").asLong())
				.isEqualTo(draftId);
			assertThat(listing.path("reports").get(0).path("latest").asBoolean()).isTrue();
			assertThat(listing.path("collectingZones").size()).isZero();

			JsonNode detail = detail(draftId);
			publishedDetails.add(detail);
			assertThat(detail.path("periodEnd").asText()).isEqualTo(cutoff.toString());
			assertThat(detail.path("periodStart").asText())
				.isEqualTo(cutoff.minusSeconds(WEEK_SECONDS).toString());
			assertThat(detail.path("publishedAt").asText())
				.isEqualTo(cutoff.plusSeconds(3 * 3600).toString());
			assertThat(detail.path("validVoteCount").asLong()).isEqualTo(week == 1 ? 102 : 101);
			assertThat(detail.path("participantCount").asLong()).isEqualTo(week == 1 ? 12 : 11);
			assertThat(detail.path("collectingZones").get(0).path("zoneId").asText())
				.isEqualTo("sim-e");
			assertThat(detail.path("collectingZones").get(0).path("validVoteCount").asInt())
				.isEqualTo(week + 1 + (week > 0 ? 1 : 0));
			assertThat(detail.path("collectingZones").get(0)
				.path("requiredValidVoteCount").asInt()).isEqualTo(50);
			assertThat(detail.path("nearBoundaryZones").size()).isGreaterThan(0);
			assertThat(detail.path("summary").asText()).isNotBlank();
			if (week == 0) {
				assertThat(detail.path("summary").asText()).contains("첫 민심 급지");
				assertThat(detail.path("summary").asText()).doesNotContain("지난 리포트");
				assertThat(detail.path("changedGradeZoneCount").isNull()).isTrue();
				assertThat(detail.path("risenZones").size()).isZero();
				assertThat(detail.path("fallenZones").size()).isZero();
				assertThat(detail.path("previousReportId").isNull()).isTrue();
			} else {
				assertThat(detail.path("previousReportId").asLong())
					.isEqualTo(reportIds.get(week - 1));
				assertThat(detail.path("changedGradeZoneCount").asInt())
					.isEqualTo(CHANGED_GRADES[week]);
				assertThat(zoneIds(detail.path("risenZones")))
					.containsExactly(EXPECTED_RISES[week]);
				assertThat(zoneIds(detail.path("fallenZones")))
					.containsExactly(EXPECTED_FALLS[week]);
				assertThat(detail.path("cityChanges").size()).isEqualTo(2);
				assertThat(detail.path("summary").asText())
					.contains("많이 오른", "많이 내린");
				detail.path("cityChanges").forEach(city ->
					assertThat(city.path("comparedZoneCount").asInt()).isEqualTo(2));
			}
			System.out.printf("REPORT_MONTH_WEEK %d %s visitors=%d votes=%d gradeChanges=%s risen=%s fallen=%s cities=%d%n",
				week + 1, cutoff.atZone(ZoneId.of("Asia/Seoul")).toLocalDate(),
				detail.path("participantCount").asInt(), detail.path("validVoteCount").asInt(),
				detail.path("changedGradeZoneCount").isNull() ? "null"
					: detail.path("changedGradeZoneCount").asText(),
				String.join(",", zoneIds(detail.path("risenZones"))),
				String.join(",", zoneIds(detail.path("fallenZones"))),
				detail.path("cityChanges").size());
			System.out.printf("REPORT_MONTH_SUMMARY %d %s%n", week + 1,
				detail.path("summary").asText());
		}

		assertThat(jdbcTemplate.queryForObject("SELECT count(*) FROM worldcup_vote", Integer.class))
			.isEqualTo(406);
		assertThat(jdbcTemplate.queryForObject("SELECT count(*) FROM weekly_report", Integer.class))
			.isEqualTo(4);
		for (int week = 0; week < reportIds.size(); week++) {
			JsonNode original = publishedDetails.get(week);
			JsonNode current = detail(reportIds.get(week));
			// 이전·다음 탐색 링크는 새 발행본에 따라 바뀌지만 저장된 내용은 불변이다.
			ObjectNode originalContent = (ObjectNode) original.deepCopy();
			ObjectNode currentContent = (ObjectNode) current.deepCopy();
			originalContent.remove("nextReportId");
			currentContent.remove("nextReportId");
			assertThat(currentContent).isEqualTo(originalContent);
			if (week + 1 < reportIds.size()) {
				assertThat(current.path("nextReportId").asLong())
					.isEqualTo(reportIds.get(week + 1));
			}
		}
	}

	private void requireCloneDatabase() {
		String database = jdbcTemplate.queryForObject("SELECT current_database()", String.class);
		if (!CLONE_DB.equals(database)) {
			throw new IllegalStateException("전용 복제 DB에서만 실행할 수 있습니다: " + database);
		}
	}

	private void resetClonedVotesAndReports() {
		jdbcTemplate.execute("TRUNCATE weekly_report_zone_snapshot, weekly_report_attempt_failure, weekly_report RESTART IDENTITY CASCADE");
		jdbcTemplate.execute("TRUNCATE worldcup_vote, worldcup_skip, worldcup_game RESTART IDENTITY CASCADE");
		jdbcTemplate.execute("DELETE FROM worldcup_zone_vote_status");
	}

	private void seedZonesAndComplexes() {
		jdbcTemplate.update("INSERT INTO region (code, name, display_order) VALUES ('report-sim', '리포트 시뮬레이션', 99)");
		jdbcTemplate.update("""
			INSERT INTO city (id, region_code, name, polygon, center)
			VALUES ('sima', 'report-sim', '테스트 가구',
			        ST_GeomFromText('MULTIPOLYGON(((0 0, 2 0, 2 2, 0 2, 0 0)))', 4326),
			        ST_GeomFromText('POINT(1 1)', 4326)),
			       ('simb', 'report-sim', '테스트 나구',
			        ST_GeomFromText('MULTIPOLYGON(((2 0, 4 0, 4 2, 2 2, 2 0)))', 4326),
			        ST_GeomFromText('POINT(3 1)', 4326))
			""");
		String[][] zones = {
			{"sim-a", "sima", "가 권역", "2.0"},
			{"sim-b", "sima", "나 권역", "2.1"},
			{"sim-c", "simb", "다 권역", "3.0"},
			{"sim-d", "simb", "라 권역", "3.1"},
			{"sim-e", "simb", "수집 권역", "4.0"}
		};
		for (int index = 0; index < zones.length; index++) {
			String[] zone = zones[index];
			jdbcTemplate.update("""
				INSERT INTO zone (id, city_id, name, dong_codes, polygon, center, initial_tier)
				VALUES (?, ?, ?, ARRAY[?],
				        ST_GeomFromText('MULTIPOLYGON(((0 0, 1 0, 1 1, 0 1, 0 0)))', 4326),
				        ST_GeomFromText('POINT(0.5 0.5)', 4326), ?)
				""", zone[0], zone[1], zone[2], "sim" + index,
				new java.math.BigDecimal(zone[3]));
			for (int complex = 1; complex <= 2; complex++) {
				jdbcTemplate.update("""
					INSERT INTO complex
					(id, zone_id, name, address_jibun, households, vote_initial_rating)
					VALUES (?, ?, ?, '테스트', 100, ?)
					""", zone[0] + complex, zone[0], zone[2] + complex,
					-Double.parseDouble(zone[3]));
			}
		}
	}

	private void seedWeek(int week, Instant cutoff) {
		String[][] priorities = {
			{"sim-a", "sim-b", "sim-c", "sim-d"},
			{"sim-a", "sim-c", "sim-b", "sim-d"},
			{"sim-d", "sim-b", "sim-a", "sim-c"},
			{"sim-b", "sim-a", "sim-d", "sim-c"}
		};
		for (int visitor = 0; visitor < 10; visitor++) {
			UUID id = visitorId(week, visitor);
			long gameId = game(id, cutoff.minusSeconds(3600));
			for (int round = 0; round < PAIRS.length; round++) {
				String[] pair = PAIRS[round];
				boolean chooseLeft = week == 0 ? visitor % 2 == 0
					: indexOf(priorities[week], pair[2]) < indexOf(priorities[week], pair[3]);
				vote(gameId, round + 1, pair, chooseLeft,
					cutoff.minusSeconds(1800 - round));
			}
			if (week == 0 && visitor == 0) {
				// 같은 방문자의 같은 날 동일 단지 대진은 저장돼도 유효표에서 제외된다.
				long duplicateGame = game(id, cutoff.minusSeconds(3000));
				vote(duplicateGame, 1, PAIRS[0], true, cutoff.minusSeconds(1700));
			}
		}
		long collectingGame = game(visitorId(week, 10), cutoff.minusSeconds(3600));
		vote(collectingGame, 1,
			new String[]{"sim-e1", "sim-a2", "sim-e", "sim-a"}, true,
			cutoff.minusSeconds(1200));
	}

	private void seedBoundaryVote(Instant cutoff) {
		long gameId = game(visitorId(4, 11), cutoff.minusSeconds(1));
		vote(gameId, 1, new String[]{"sim-e2", "sim-c1", "sim-e", "sim-c"},
			true, cutoff);
	}

	private long game(UUID visitor, Instant startedAt) {
		return jdbcTemplate.queryForObject("""
			INSERT INTO worldcup_game
			(anonymous_id, base_zone_id, tier_range_used, matchups, started_at)
			VALUES (?, 'sim-a', 0.2, ?::jsonb, ?) RETURNING id
			""", Long.class, visitor, MATCHUPS, utc(startedAt));
	}

	private void vote(long gameId, int round, String[] pair,
		boolean chooseLeft, Instant selectedAt) {
		jdbcTemplate.update("""
			INSERT INTO worldcup_vote
			(game_id, round_number, left_complex_id, right_complex_id,
		 selected_complex_id, left_zone_id, right_zone_id, selected_at)
			VALUES (?, ?, ?, ?, ?, ?, ?, ?)
			""", gameId, round, pair[0], pair[1],
			chooseLeft ? pair[0] : pair[1], pair[2], pair[3], utc(selectedAt));
	}

	private JsonNode list() throws Exception {
		return response("/api/v1/reports");
	}

	private JsonNode detail(long reportId) throws Exception {
		return response("/api/v1/reports/" + reportId);
	}

	private JsonNode response(String path) throws Exception {
		String content = mockMvc.perform(get(path)).andExpect(status().isOk())
			.andReturn().getResponse().getContentAsString();
		return objectMapper.readTree(content);
	}

	private static String[] zoneIds(JsonNode items) {
		List<String> ids = new ArrayList<>();
		items.forEach(item -> ids.add(item.path("zoneId").asText()));
		return ids.toArray(String[]::new);
	}

	private static UUID visitorId(int week, int visitor) {
		return UUID.nameUUIDFromBytes(("report-sim-" + week + "-" + visitor)
			.getBytes(StandardCharsets.UTF_8));
	}

	private static int indexOf(String[] values, String target) {
		for (int index = 0; index < values.length; index++) {
			if (values[index].equals(target)) {
				return index;
			}
		}
		throw new IllegalArgumentException("우선순위에 없는 권역: " + target);
	}

	private static OffsetDateTime utc(Instant instant) {
		return OffsetDateTime.ofInstant(instant, ZoneOffset.UTC);
	}

	@TestConfiguration
	static class ClockConfig {
		@Bean
		@Primary
		MutableClock reportMonthClock() {
			return new MutableClock();
		}
	}

	static final class MutableClock extends Clock {
		private final AtomicReference<Instant> now = new AtomicReference<>(FIRST_CUTOFF);

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
