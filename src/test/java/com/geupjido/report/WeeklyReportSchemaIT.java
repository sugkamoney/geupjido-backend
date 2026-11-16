package com.geupjido.report;

import java.time.OffsetDateTime;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.condition.EnabledIfEnvironmentVariable;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.transaction.annotation.Transactional;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

@EnabledIfEnvironmentVariable(named = "RUN_POSTGIS_IT", matches = "true")
@SpringBootTest
@ActiveProfiles("local")
@Transactional
class WeeklyReportSchemaIT {
	private static final OffsetDateTime START =
		OffsetDateTime.parse("2026-11-08T09:00:00Z");
	private static final OffsetDateTime END =
		OffsetDateTime.parse("2026-11-15T09:00:00Z");
	private static final OffsetDateTime COMPLETED =
		OffsetDateTime.parse("2026-11-15T09:01:00Z");
	private static final OffsetDateTime RELEASE =
		OffsetDateTime.parse("2026-11-15T12:00:00Z");

	@Autowired private JdbcTemplate jdbcTemplate;

	@Test
	void 일요일_18시_마감_초안을_권역_상태와_함께_보존하고_21시에_발행한다() {
		long reportId = insertDraft(START, END, COMPLETED);
		jdbcTemplate.update("""
			INSERT INTO weekly_report_zone_snapshot
			(report_id, zone_id, zone_name, city_id, city_name, region_code,
			 region_name, ranking_basis, vote_tier, valid_vote_count,
			 participant_count, cross_zone_vote_count)
			VALUES (?, 'test-vote-zone', '테스트 권역', 'test-city', '테스트 구',
			        'seoul', '서울', 'VOTE', 2.3, 55, 12, 21),
			       (?, 'test-collecting-zone', '모으는 권역', 'test-city', '테스트 구',
			        'seoul', '서울', 'INITIAL', NULL, 49, 9, 19)
			""", reportId, reportId);
		jdbcTemplate.update("""
			UPDATE weekly_report
			SET published_at = ?, period_valid_vote_count = 80,
			    period_participant_count = 15, changed_grade_zone_count = NULL
			WHERE id = ?
			""", RELEASE, reportId);

		assertThat(jdbcTemplate.queryForObject("""
			SELECT count(*) FROM weekly_report
			WHERE id = ? AND published_at IS NOT NULL
			  AND period_valid_vote_count = 80 AND period_participant_count = 15
			""", Integer.class, reportId)).isEqualTo(1);
		assertThat(jdbcTemplate.queryForObject("""
			SELECT count(*) FROM weekly_report_zone_snapshot
			WHERE report_id = ? AND ranking_basis = 'VOTE' AND vote_tier = 2.3
			""", Integer.class, reportId)).isEqualTo(1);
		assertThat(jdbcTemplate.queryForObject("""
			SELECT count(*) FROM weekly_report_zone_snapshot
			WHERE report_id = ? AND ranking_basis = 'INITIAL' AND vote_tier IS NULL
			""", Integer.class, reportId)).isEqualTo(1);
	}

	@Test
	void 같은_주간을_두_번_저장하지_못한다() {
		insertDraft(START, END, COMPLETED);
		assertThatThrownBy(() -> insertDraft(START, END, COMPLETED))
			.isInstanceOf(DataIntegrityViolationException.class);
	}

	@Test
	void 일요일_18시가_아닌_마감은_거부한다() {
		OffsetDateTime wrongStart = START.plusHours(1);
		OffsetDateTime wrongEnd = END.plusHours(1);
		assertThatThrownBy(() -> insertDraft(wrongStart, wrongEnd,
			COMPLETED.plusHours(1)))
			.isInstanceOf(DataIntegrityViolationException.class);
	}

	@Test
	void 발행_시각은_마감_집계보다_빠를_수_없다() {
		long reportId = insertDraft(START, END, COMPLETED);
		assertThatThrownBy(() -> jdbcTemplate.update("""
			UPDATE weekly_report
			SET published_at = ?, period_valid_vote_count = 1,
			    period_participant_count = 1
			WHERE id = ?
			""", END, reportId))
			.isInstanceOf(DataIntegrityViolationException.class);
	}

	@Test
	void 집계가_끝났어도_일요일_21시_전에는_발행하지_못한다() {
		long reportId = insertDraft(START, END, COMPLETED);
		assertThatThrownBy(() -> jdbcTemplate.update("""
			UPDATE weekly_report
			SET published_at = ?, period_valid_vote_count = 1,
			    period_participant_count = 1
			WHERE id = ?
			""", RELEASE.minusSeconds(1), reportId))
			.isInstanceOf(DataIntegrityViolationException.class);
	}

	@Test
	void 발행한_리포트는_다시_수정하지_못한다() {
		long reportId = insertDraft(START, END, COMPLETED);
		jdbcTemplate.update("""
			UPDATE weekly_report
			SET published_at = ?, period_valid_vote_count = 1,
			    period_participant_count = 1
			WHERE id = ?
			""", RELEASE, reportId);
		assertThatThrownBy(() -> jdbcTemplate.update("""
			UPDATE weekly_report SET summary_text = '수정된 요약' WHERE id = ?
			""", reportId))
			.isInstanceOf(DataIntegrityViolationException.class);
	}

	@Test
	void 발행한_리포트는_삭제하지_못한다() {
		long reportId = insertDraft(START, END, COMPLETED);
		jdbcTemplate.update("""
			UPDATE weekly_report
			SET published_at = ?, period_valid_vote_count = 1,
			    period_participant_count = 1
			WHERE id = ?
			""", RELEASE, reportId);
		assertThatThrownBy(() -> jdbcTemplate.update(
			"DELETE FROM weekly_report WHERE id = ?", reportId))
			.isInstanceOf(DataIntegrityViolationException.class);
	}

	@Test
	void 발행_후에는_권역_스냅샷을_추가하지_못한다() {
		long reportId = insertDraft(START, END, COMPLETED);
		jdbcTemplate.update("""
			UPDATE weekly_report
			SET published_at = ?, period_valid_vote_count = 1,
			    period_participant_count = 1
			WHERE id = ?
			""", RELEASE, reportId);
		assertThatThrownBy(() -> jdbcTemplate.update("""
			INSERT INTO weekly_report_zone_snapshot
			(report_id, zone_id, zone_name, city_id, city_name, region_code,
			 region_name, ranking_basis, vote_tier, valid_vote_count,
			 participant_count, cross_zone_vote_count)
			VALUES (?, 'late-zone', '늦은 권역', 'test-city', '테스트 구',
			        'seoul', '서울', 'VOTE', 2.3, 1, 1, 0)
			""", reportId))
			.isInstanceOf(DataIntegrityViolationException.class);
	}

	private long insertDraft(OffsetDateTime start, OffsetDateTime end,
		OffsetDateTime completed) {
		Long id = jdbcTemplate.queryForObject("""
			INSERT INTO weekly_report
			(period_start_at, period_end_at, cutoff_completed_at)
			VALUES (?, ?, ?) RETURNING id
			""", Long.class, start, end, completed);
		return java.util.Objects.requireNonNull(id);
	}
}
