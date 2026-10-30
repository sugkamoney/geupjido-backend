package com.geupjido.trade.repository;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.List;
import java.util.Map;

import org.springframework.jdbc.core.namedparam.NamedParameterJdbcTemplate;
import org.springframework.stereotype.Repository;

/** 일반 아파트 매매가 없는 단지의 권리 거래 후보만 읽는다. */
@Repository
public class ApartmentPresaleTradeRepository {

	private final NamedParameterJdbcTemplate jdbcTemplate;

	public ApartmentPresaleTradeRepository(
		NamedParameterJdbcTemplate jdbcTemplate
	) {
		this.jdbcTemplate = jdbcTemplate;
	}

	public List<Candidate> findRepresentativeCandidates(List<String> complexIds) {
		if (complexIds.isEmpty()) {
			return List.of();
		}
		return jdbcTemplate.query("""
			SELECT p.id, p.complex_id, p.deal_date, p.deal_amount,
			       p.exclusive_area
			FROM apartment_presale_trade p
			JOIN complex c ON c.id = p.complex_id
			WHERE p.complex_id IN (:complexIds)
			  AND p.zone_id = c.zone_id
			  AND p.is_canceled = FALSE
			  AND NOT EXISTS (
			      SELECT 1 FROM apartment_presale_trade canceled
			      WHERE canceled.complex_id = p.complex_id
			        AND canceled.deal_date = p.deal_date
			        AND canceled.deal_amount = p.deal_amount
			        AND canceled.exclusive_area = p.exclusive_area
			        AND canceled.floor IS NOT DISTINCT FROM p.floor
			        AND canceled.is_canceled = TRUE
			  )
			ORDER BY p.complex_id,
			         CASE WHEN p.exclusive_area >= 58 AND p.exclusive_area < 61
			              THEN 0
			              WHEN p.exclusive_area BETWEEN 44 AND 65 THEN 1
			              ELSE 2 END,
			         CASE WHEN p.exclusive_area < 44 OR p.exclusive_area > 65
			              THEN ABS(p.exclusive_area - 59) ELSE 0 END,
			         p.deal_date DESC, p.deal_amount, p.id
			""", Map.of("complexIds", complexIds),
			(rs, row) -> new Candidate(
				rs.getLong("id"), rs.getString("complex_id"),
				rs.getDate("deal_date").toLocalDate(),
				rs.getLong("deal_amount"),
				rs.getBigDecimal("exclusive_area")
			));
	}

	public record Candidate(
		long id,
		String complexId,
		LocalDate dealDate,
		long amount,
		BigDecimal area
	) {
	}
}
