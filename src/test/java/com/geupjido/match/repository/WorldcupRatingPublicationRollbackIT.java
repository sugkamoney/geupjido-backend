package com.geupjido.match.repository;

import java.math.BigDecimal;
import java.time.Instant;
import java.util.List;
import java.util.Map;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.condition.EnabledIfEnvironmentVariable;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.dao.InvalidDataAccessApiUsageException;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.transaction.PlatformTransactionManager;
import org.springframework.transaction.TransactionDefinition;
import org.springframework.transaction.support.TransactionTemplate;

import com.geupjido.match.service.WorldcupVoteAggregationInputService.RatingInput;
import com.geupjido.match.service.WorldcupVoteEligibilityPolicy.ZoneCount;
import com.geupjido.match.service.WorldcupVoteRatingCalculator.ComplexSeed;
import com.geupjido.match.service.WorldcupVoteRatingCalculator.Snapshot;
import com.geupjido.match.service.WorldcupVoteRatingCalculator.ZoneScore;
import com.geupjido.match.service.WorldcupVoteRatingCalculator.ZoneSeed;
import com.geupjido.match.service.WorldcupVoteRatingService.Calculation;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

@EnabledIfEnvironmentVariable(named = "RUN_POSTGIS_IT", matches = "true")
@SpringBootTest
@ActiveProfiles("local")
class WorldcupRatingPublicationRollbackIT {

	@Autowired private JdbcTemplate jdbcTemplate;
	@Autowired private PlatformTransactionManager transactionManager;
	@Autowired private WorldcupVoteRatingPublicationRepository publicationRepository;

	@Test
	void 단지_저장에_실패하면_앞서_쓴_초기값과_권역_결과도_롤백한다() {
		String complexId = jdbcTemplate.queryForObject(
			"SELECT id FROM complex WHERE price > 0 AND representative_area > 0 "
				+ "ORDER BY id LIMIT 1", String.class);
		String otherZoneId = jdbcTemplate.queryForObject(
			"SELECT id FROM zone WHERE id <> (SELECT zone_id FROM complex WHERE id = ?) "
				+ "ORDER BY id LIMIT 1", String.class, complexId);
		Double originalSeed = jdbcTemplate.queryForObject(
			"SELECT vote_initial_rating FROM complex WHERE id = ?", Double.class,
			complexId);
		Map<String, Object> originalZone = jdbcTemplate.queryForMap(
			"SELECT tier, score, ranking_basis, tier_updated_at FROM zone WHERE id = ?",
			otherZoneId);
		var calculation = new Calculation(
			new WorldcupVoteRatingSeedRepository.Seeds(
				List.of(new ZoneSeed(otherZoneId, new BigDecimal("2.0"))),
				List.of(new ComplexSeed(complexId, otherZoneId, 100000,
					new BigDecimal("59"), null))),
			new RatingInput(
				Map.of(otherZoneId, new ZoneCount(50, 10, 20)),
				new Snapshot(
					Map.of(complexId, new BigDecimal("80.00")),
					Map.of(complexId, 1),
					Map.of(otherZoneId, new ZoneScore(
						new BigDecimal("80.00"), new BigDecimal("2.0")))),
				Map.of(complexId, -2.0)));
		TransactionTemplate transaction = new TransactionTemplate(transactionManager);
		transaction.setIsolationLevel(TransactionDefinition.ISOLATION_REPEATABLE_READ);

		assertThatThrownBy(() -> transaction.execute(status -> {
			jdbcTemplate.update("UPDATE complex SET vote_initial_rating = NULL WHERE id = ?",
				complexId);
			publicationRepository.lockPublication();
			publicationRepository.publish(calculation, Instant.now());
			return null;
		})).isInstanceOf(InvalidDataAccessApiUsageException.class)
			.hasMessageContaining("단지 투표 결과 저장 실패");
		assertThat(jdbcTemplate.queryForObject(
			"SELECT vote_initial_rating FROM complex WHERE id = ?", Double.class,
			complexId)).isEqualTo(originalSeed);
		assertThat(jdbcTemplate.queryForMap(
			"SELECT tier, score, ranking_basis, tier_updated_at FROM zone WHERE id = ?",
			otherZoneId)).isEqualTo(originalZone);
	}
}
