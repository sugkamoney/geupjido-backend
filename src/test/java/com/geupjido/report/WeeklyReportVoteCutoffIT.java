package com.geupjido.report;

import java.time.Instant;
import java.time.OffsetDateTime;
import java.time.ZoneOffset;
import java.util.ArrayList;
import java.util.List;
import java.util.UUID;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.condition.EnabledIfEnvironmentVariable;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.transaction.annotation.Transactional;

import com.geupjido.match.model.WorldcupVoteForAggregation;
import com.geupjido.match.repository.WorldcupVoteAggregationRepository;
import com.geupjido.match.service.WorldcupVoteAggregationInputService;
import com.geupjido.match.service.WorldcupVoteRatingService;

import static org.assertj.core.api.Assertions.assertThat;

@EnabledIfEnvironmentVariable(named = "RUN_POSTGIS_IT", matches = "true")
@SpringBootTest
@ActiveProfiles("local")
@Transactional
class WeeklyReportVoteCutoffIT {
	private static final Instant CUTOFF = Instant.parse("2026-11-15T09:00:00Z");

	@Autowired private JdbcTemplate jdbcTemplate;
	@Autowired private WorldcupVoteAggregationRepository repository;
	@Autowired private WorldcupVoteAggregationInputService aggregationInputService;
	@Autowired private WorldcupVoteRatingService ratingService;

	@Test
	void 일요일_18시_직전_표만_포함하고_정각_표는_제외한다() {
		jdbcTemplate.update("""
			INSERT INTO region (code, name, display_order)
			VALUES ('report-test', '리포트 테스트', 99)
			""");
		jdbcTemplate.update("""
			INSERT INTO city (id, region_code, name, polygon, center)
			VALUES ('reportcity', 'report-test', '테스트 구',
			        ST_GeomFromText('MULTIPOLYGON(((0 0, 1 0, 1 1, 0 1, 0 0)))', 4326),
			        ST_GeomFromText('POINT(0.5 0.5)', 4326))
			""");
		jdbcTemplate.update("""
			INSERT INTO zone (id, city_id, name, dong_codes, polygon, center, initial_tier)
			VALUES ('report-zone', 'reportcity', '테스트 권역', ARRAY['report'],
			        ST_GeomFromText('MULTIPOLYGON(((0 0, 1 0, 1 1, 0 1, 0 0)))', 4326),
			        ST_GeomFromText('POINT(0.5 0.5)', 4326), 2.3)
			""");
		jdbcTemplate.update("""
			INSERT INTO complex (id, zone_id, name, address_jibun, households, vote_initial_rating)
			VALUES ('report-left-1', 'report-zone', '왼쪽 1', '테스트', 100, -2.3),
			       ('report-right-1', 'report-zone', '오른쪽 1', '테스트', 100, -2.3),
			       ('report-left-2', 'report-zone', '왼쪽 2', '테스트', 100, -2.3),
			       ('report-right-2', 'report-zone', '오른쪽 2', '테스트', 100, -2.3)
			""");
		Long gameId = jdbcTemplate.queryForObject("""
			INSERT INTO worldcup_game
			(anonymous_id, base_zone_id, tier_range_used, matchups, started_at)
			VALUES (?, 'report-zone', 0.2,
			        '[{}, {}, {}, {}, {}, {}, {}, {}, {}, {}]'::jsonb, ?)
			RETURNING id
			""", Long.class, UUID.randomUUID(), utc(CUTOFF.minusSeconds(3600)));
		jdbcTemplate.update("""
			INSERT INTO worldcup_vote
			(game_id, round_number, left_complex_id, right_complex_id,
			 selected_complex_id, left_zone_id, right_zone_id, selected_at)
			VALUES (?, 1, 'report-left-1', 'report-right-1',
			        'report-left-1', 'report-zone', 'report-zone', ?),
			       (?, 2, 'report-left-2', 'report-right-2',
			        'report-left-2', 'report-zone', 'report-zone', ?)
			""", gameId, utc(CUTOFF.minusSeconds(1)), gameId, utc(CUTOFF));

		List<WorldcupVoteForAggregation> recorded = new ArrayList<>();
		repository.forEachRecordedVoteBefore(CUTOFF, recorded::add);
		assertThat(recorded.stream().filter(vote -> vote.gameId() == gameId)
			.map(WorldcupVoteForAggregation::roundNumber)).containsExactly(1);

		List<WorldcupVoteForAggregation> valid = new ArrayList<>();
		aggregationInputService.forEachValidVoteBefore(CUTOFF, valid::add);
		assertThat(valid.stream().filter(vote -> vote.gameId() == gameId)
			.map(WorldcupVoteForAggregation::roundNumber)).containsExactly(1);
		assertThat(ratingService.calculateBefore(CUTOFF).input().validVoteCount()).isEqualTo(1);
		assertThat(ratingService.calculate().input().validVoteCount()).isEqualTo(2);
	}

	private static OffsetDateTime utc(Instant instant) {
		return OffsetDateTime.ofInstant(instant, ZoneOffset.UTC);
	}
}
