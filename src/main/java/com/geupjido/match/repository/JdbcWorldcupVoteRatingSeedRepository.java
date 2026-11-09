package com.geupjido.match.repository;

import java.util.List;

import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Repository;

import com.geupjido.match.service.WorldcupVoteRatingCalculator.ComplexSeed;
import com.geupjido.match.service.WorldcupVoteRatingCalculator.ZoneSeed;

@Repository
public class JdbcWorldcupVoteRatingSeedRepository implements WorldcupVoteRatingSeedRepository {

	private final JdbcTemplate jdbcTemplate;

	public JdbcWorldcupVoteRatingSeedRepository(JdbcTemplate jdbcTemplate) {
		this.jdbcTemplate = jdbcTemplate;
	}

	@Override
	public Seeds loadSeeds() {
		List<ZoneSeed> zones = jdbcTemplate.query(
			"SELECT id, initial_tier FROM zone WHERE initial_tier IS NOT NULL ORDER BY id",
			(resultSet, rowNumber) -> new ZoneSeed(
				resultSet.getString("id"),
				resultSet.getBigDecimal("initial_tier")
			)
		);
		List<ComplexSeed> complexes = jdbcTemplate.query(
			"""
			SELECT c.id, c.zone_id, c.price, c.representative_area, c.vote_initial_rating
			FROM complex AS c
			JOIN zone AS z ON z.id = c.zone_id AND z.initial_tier IS NOT NULL
			WHERE (c.price > 0 AND c.representative_area > 0)
			   OR c.vote_initial_rating IS NOT NULL
			ORDER BY c.id
			""",
			(resultSet, rowNumber) -> new ComplexSeed(
				resultSet.getString("id"),
				resultSet.getString("zone_id"),
				resultSet.getObject("price", Integer.class),
				resultSet.getBigDecimal("representative_area"),
				resultSet.getObject("vote_initial_rating", Double.class)
			)
		);
		return new Seeds(zones, complexes);
	}
}
