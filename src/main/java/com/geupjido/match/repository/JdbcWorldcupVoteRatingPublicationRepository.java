package com.geupjido.match.repository;

import java.time.Instant;
import java.time.LocalDateTime;
import java.time.OffsetDateTime;
import java.time.ZoneOffset;

import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Repository;

import com.geupjido.match.service.WorldcupVoteRatingCalculator.ComplexSeed;
import com.geupjido.match.service.WorldcupVoteRatingService.Calculation;

@Repository
public class JdbcWorldcupVoteRatingPublicationRepository
	implements WorldcupVoteRatingPublicationRepository {

	private final JdbcTemplate jdbcTemplate;

	public JdbcWorldcupVoteRatingPublicationRepository(JdbcTemplate jdbcTemplate) {
		this.jdbcTemplate = jdbcTemplate;
	}

	@Override
	public void lockPublication() {
		Integer id = jdbcTemplate.queryForObject(
			"SELECT id FROM worldcup_rating_publication WHERE id = 1 FOR UPDATE",
			Integer.class
		);
		if (id == null) {
			throw new IllegalStateException("월드컵 집계 발행 행이 없습니다.");
		}
	}

	@Override
	public void publish(Calculation calculation, Instant completedAt) {
		var input = calculation.input();
		var ratings = input.ratings();
		for (ComplexSeed seed : calculation.seeds().complexes()) {
			if (seed.initialRating() == null) {
				requireOne(jdbcTemplate.update(
					"UPDATE complex SET vote_initial_rating = ? "
						+ "WHERE id = ? AND vote_initial_rating IS NULL",
					input.initialComplexRatings().get(seed.id()), seed.id()
				), "단지 초기 평가값 저장 실패: " + seed.id());
			}
		}

		LocalDateTime completedUtc = LocalDateTime.ofInstant(completedAt, ZoneOffset.UTC);
		for (var entry : input.zoneCounts().entrySet()) {
			if (!entry.getValue().readyForVoteRanking()) {
				continue;
			}
			String zoneId = entry.getKey();
			var zoneScore = ratings.zoneScores().get(zoneId);
			if (zoneScore == null) {
				throw new IllegalStateException("권역 평가값이 없습니다: " + zoneId);
			}
			requireOne(jdbcTemplate.update(
				"""
				UPDATE zone
				SET tier = ?, score = ?, ranking_basis = 'VOTE', tier_updated_at = ?
				WHERE id = ?
				""",
				zoneScore.tier(), zoneScore.score(), completedUtc, zoneId
			), "권역 투표 결과 저장 실패: " + zoneId);
		}

		for (ComplexSeed seed : calculation.seeds().complexes()) {
			var count = input.zoneCounts().get(seed.zoneId());
			if (count == null || !count.readyForVoteRanking()) {
				continue;
			}
			requireOne(jdbcTemplate.update(
				"""
				UPDATE complex SET score = ?, zone_rank = ?
				WHERE id = ? AND zone_id = ?
				""",
				ratings.complexScores().get(seed.id()),
				ratings.zoneRanks().get(seed.id()), seed.id(), seed.zoneId()
			), "단지 투표 결과 저장 실패: " + seed.id());
		}

		requireOne(jdbcTemplate.update(
			"UPDATE worldcup_rating_publication SET last_completed_at = ? WHERE id = 1",
			OffsetDateTime.ofInstant(completedAt, ZoneOffset.UTC)
		), "월드컵 집계 완료 시각 저장 실패");
	}

	private static void requireOne(int affectedRows, String message) {
		if (affectedRows != 1) {
			throw new IllegalStateException(message);
		}
	}
}
