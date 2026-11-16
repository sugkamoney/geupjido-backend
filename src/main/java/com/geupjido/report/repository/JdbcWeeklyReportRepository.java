package com.geupjido.report.repository;

import java.time.Instant;
import java.time.OffsetDateTime;
import java.time.ZoneOffset;
import java.util.List;
import java.util.Objects;
import java.util.Optional;

import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.jdbc.core.RowMapper;
import org.springframework.stereotype.Repository;

import com.geupjido.report.model.WeeklyReportPeriod;
import com.geupjido.report.model.WeeklyReportSource.Report;
import com.geupjido.report.model.WeeklyReportSource.CollectingProgress;
import com.geupjido.report.model.WeeklyReportSource.Zone;
import com.geupjido.report.model.WeeklyReportSource.ZoneIdentity;
import com.geupjido.zone.domain.RankingBasis;

@Repository
public class JdbcWeeklyReportRepository implements WeeklyReportRepository {
	private static final String REPORT_COLUMNS = """
		id, period_start_at, period_end_at, cutoff_completed_at, published_at,
		period_valid_vote_count, period_participant_count,
		changed_grade_zone_count, summary_text
		""";
	private static final RowMapper<Report> REPORT_MAPPER = (rs, row) ->
		new Report(rs.getLong("id"), instant(rs.getObject("period_start_at", OffsetDateTime.class)),
			instant(rs.getObject("period_end_at", OffsetDateTime.class)),
			instant(rs.getObject("cutoff_completed_at", OffsetDateTime.class)),
			instant(rs.getObject("published_at", OffsetDateTime.class)),
			rs.getLong("period_valid_vote_count"), rs.getLong("period_participant_count"),
			rs.getObject("changed_grade_zone_count", Integer.class), rs.getString("summary_text"));

	private final JdbcTemplate jdbcTemplate;

	public JdbcWeeklyReportRepository(JdbcTemplate jdbcTemplate) {
		this.jdbcTemplate = jdbcTemplate;
	}

	@Override
	public void lockPublication() {
		Integer id = jdbcTemplate.queryForObject(
			"SELECT id FROM worldcup_rating_publication WHERE id = 1 FOR UPDATE",
			Integer.class);
		if (id == null) {
			throw new IllegalStateException("급지 집계 발행 행이 없습니다.");
		}
	}

	@Override
	public boolean existsForPeriod(Instant periodEndAt) {
		Integer count = jdbcTemplate.queryForObject(
			"SELECT count(*) FROM weekly_report WHERE period_end_at = ?",
			Integer.class, utc(periodEndAt));
		return count != null && count > 0;
	}

	@Override
	public boolean hasDraftForPeriod(Instant periodEndAt) {
		Integer count = jdbcTemplate.queryForObject("""
			SELECT count(*) FROM weekly_report
			WHERE period_end_at = ? AND published_at IS NULL
			""", Integer.class, utc(periodEndAt));
		return count != null && count > 0;
	}

	@Override
	public boolean hasFailedAttempt(Instant periodEndAt) {
		Integer count = jdbcTemplate.queryForObject(
			"SELECT count(*) FROM weekly_report_attempt_failure WHERE period_end_at = ?",
			Integer.class, utc(periodEndAt));
		return count != null && count > 0;
	}

	@Override
	public void recordFailedAttempt(Instant periodEndAt, Instant failedAt) {
		jdbcTemplate.update("""
			INSERT INTO weekly_report_attempt_failure (period_end_at, failed_at)
			VALUES (?, ?)
			ON CONFLICT (period_end_at) DO UPDATE SET failed_at = EXCLUDED.failed_at
			""", utc(periodEndAt), utc(failedAt));
	}

	@Override
	public void clearFailedAttempt(Instant periodEndAt) {
		jdbcTemplate.update(
			"DELETE FROM weekly_report_attempt_failure WHERE period_end_at = ?",
			utc(periodEndAt));
	}

	@Override
	public List<ZoneIdentity> currentZones() {
		return jdbcTemplate.query("""
			SELECT z.id AS zone_id, z.name AS zone_name,
			       c.id AS city_id, c.name AS city_name,
			       r.code AS region_code, r.name AS region_name,
			       z.initial_tier
			FROM zone AS z
			JOIN city AS c ON c.id = z.city_id
			JOIN region AS r ON r.code = c.region_code
			ORDER BY z.id
			""", (rs, row) -> new ZoneIdentity(rs.getString("zone_id"),
			rs.getString("zone_name"), rs.getString("city_id"),
				rs.getString("city_name"), rs.getString("region_code"),
				rs.getString("region_name"), rs.getBigDecimal("initial_tier")));
	}

	@Override
	public List<CollectingProgress> currentCollectingZones() {
		return jdbcTemplate.query("""
			SELECT z.id AS zone_id, z.name AS zone_name,
			       c.name AS city_name, r.name AS region_name,
			       s.valid_vote_count, s.participant_count, s.cross_zone_vote_count
			FROM worldcup_zone_vote_status s
			JOIN zone z ON z.id = s.zone_id
			JOIN city c ON c.id = z.city_id
			JOIN region r ON r.code = c.region_code AND r.is_active = TRUE
			WHERE z.ranking_basis = 'INITIAL' AND s.valid_vote_count > 0
			ORDER BY s.valid_vote_count DESC, z.name, z.id
			LIMIT 5
			""", (rs, row) -> new CollectingProgress(rs.getString("zone_id"),
			rs.getString("zone_name"), rs.getString("city_name"),
			rs.getString("region_name"), rs.getInt("valid_vote_count"),
			rs.getInt("participant_count"), rs.getInt("cross_zone_vote_count")));
	}

	@Override
	public Optional<Report> previousPublished(Instant periodEndAt) {
		return jdbcTemplate.query("SELECT " + REPORT_COLUMNS + """
			FROM weekly_report
			WHERE period_end_at < ? AND published_at IS NOT NULL
			ORDER BY period_end_at DESC LIMIT 1
			""", REPORT_MAPPER, utc(periodEndAt)).stream().findFirst();
	}

	@Override
	public List<Report> publishedReports() {
		return jdbcTemplate.query("SELECT " + REPORT_COLUMNS + """
			FROM weekly_report
			WHERE published_at IS NOT NULL
			ORDER BY period_end_at DESC
			""", REPORT_MAPPER);
	}

	@Override
	public Optional<Report> publishedReport(long id) {
		return jdbcTemplate.query("SELECT " + REPORT_COLUMNS + """
			FROM weekly_report
			WHERE id = ? AND published_at IS NOT NULL
			""", REPORT_MAPPER, id).stream().findFirst();
	}

	@Override
	public List<Zone> zones(long reportId) {
		return jdbcTemplate.query("""
			SELECT zone_id, zone_name, city_id, city_name, region_code,
			       region_name, ranking_basis, initial_tier, vote_tier, valid_vote_count,
			       participant_count, cross_zone_vote_count
			FROM weekly_report_zone_snapshot
			WHERE report_id = ? ORDER BY zone_id
			""", (rs, row) -> new Zone(rs.getString("zone_id"),
			rs.getString("zone_name"), rs.getString("city_id"),
			rs.getString("city_name"), rs.getString("region_code"),
				rs.getString("region_name"), RankingBasis.valueOf(rs.getString("ranking_basis")),
				rs.getBigDecimal("initial_tier"), rs.getBigDecimal("vote_tier"), rs.getInt("valid_vote_count"),
			rs.getInt("participant_count"), rs.getInt("cross_zone_vote_count")), reportId);
	}

	@Override
	public void saveDraft(WeeklyReportPeriod period, Instant completedAt, long validVotes,
		long participants, Integer changedGradeZones, String summary, List<Zone> zones) {
		Long reportId = jdbcTemplate.queryForObject("""
			INSERT INTO weekly_report
			(period_start_at, period_end_at, cutoff_completed_at,
			 period_valid_vote_count, period_participant_count,
			 changed_grade_zone_count, summary_text)
			VALUES (?, ?, ?, ?, ?, ?, ?) RETURNING id
			""", Long.class, utc(period.startAt()), utc(period.endAt()),
			utc(completedAt), validVotes, participants, changedGradeZones, summary);
		long id = Objects.requireNonNull(reportId, "저장한 리포트 ID가 없습니다.");
		for (Zone zone : zones) {
			jdbcTemplate.update("""
				INSERT INTO weekly_report_zone_snapshot
				(report_id, zone_id, zone_name, city_id, city_name,
				 region_code, region_name, ranking_basis, initial_tier, vote_tier,
				 valid_vote_count, participant_count, cross_zone_vote_count)
				VALUES (?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?)
				""", id, zone.zoneId(), zone.zoneName(), zone.cityId(), zone.cityName(),
				zone.regionCode(), zone.regionName(), zone.rankingBasis().name(),
				zone.initialTier(), zone.voteTier(), zone.validVoteCount(), zone.participantCount(),
				zone.crossZoneVoteCount());
		}
		clearFailedAttempt(period.endAt());
	}

	@Override
	public boolean publish(Instant periodEndAt, Instant publishedAt) {
		return jdbcTemplate.update("""
			UPDATE weekly_report SET published_at = ?
			WHERE period_end_at = ? AND published_at IS NULL
			""", utc(publishedAt), utc(periodEndAt)) == 1;
	}

	private static OffsetDateTime utc(Instant instant) {
		return OffsetDateTime.ofInstant(instant, ZoneOffset.UTC);
	}

	private static Instant instant(OffsetDateTime dateTime) {
		return dateTime == null ? null : dateTime.toInstant();
	}
}
