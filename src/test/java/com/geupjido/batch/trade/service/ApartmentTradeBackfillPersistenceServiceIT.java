package com.geupjido.batch.trade.service;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.List;
import java.util.UUID;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.condition.EnabledIfEnvironmentVariable;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.transaction.annotation.Transactional;

import com.geupjido.batch.trade.model.ApartmentTradeData;

import static org.assertj.core.api.Assertions.assertThat;

@EnabledIfEnvironmentVariable(named = "RUN_POSTGIS_IT", matches = "true")
@SpringBootTest
@ActiveProfiles("local")
@Transactional
class ApartmentTradeBackfillPersistenceServiceIT {

	@Autowired
	private ApartmentTradeBackfillPersistenceService service;

	@Autowired
	private JdbcTemplate jdbcTemplate;

	@Test
	void 충돌한_식별_키만_보류하고_다른_거래는_저장한다() {
		String prefix = "IT100-" + UUID.randomUUID().toString().substring(0, 8);
		ApartmentTradeData safe = trade(prefix + "-safe", false, null);
		ApartmentTradeData normal = trade(prefix + "-conflict", false, null);
		ApartmentTradeData canceled = trade(prefix + "-conflict", true,
			LocalDate.of(2026, 9, 20));

		var first = service.persist("11170", "202609",
			List.of(safe, normal, canceled));
		var second = service.persist("11170", "202609",
			List.of(safe, normal, canceled));

		assertThat(first.imported().createdCount()).isEqualTo(1);
		assertThat(first.conflictRecords()).isEqualTo(2);
		assertThat(first.conflictKeys()).isEqualTo(1);
		assertThat(second.imported().skippedCount()).isEqualTo(1);
		assertThat(count("apartment_trade", prefix + "-safe"))
			.isEqualTo(1);
		assertThat(count("apartment_trade", prefix + "-conflict"))
			.isZero();
		assertThat(count("apartment_trade_conflict", prefix + "-conflict"))
			.isEqualTo(2);
	}

	@Test
	void 기존_해제일과_다른_거래는_기존_행을_보존하고_보류한다() {
		String name = "IT100-" + UUID.randomUUID().toString().substring(0, 8);
		service.persist("11170", "202609", List.of(
			trade(name, true, LocalDate.of(2026, 9, 20))
		));

		var result = service.persist("11170", "202609", List.of(
			trade(name, true, LocalDate.of(2026, 9, 21))
		));

		assertThat(result.conflictRecords()).isEqualTo(1);
		assertThat(jdbcTemplate.queryForObject("""
			SELECT cancellation_date FROM apartment_trade
			WHERE raw_apartment_name = ?
			""", LocalDate.class, name))
			.isEqualTo(LocalDate.of(2026, 9, 20));
		assertThat(jdbcTemplate.queryForObject("""
			SELECT conflict_reason FROM apartment_trade_conflict
			WHERE raw_apartment_name = ?
			""", String.class, name))
			.isEqualTo("CANCELLATION_DATE_MISMATCH");
	}

	private long count(String table, String name) {
		return jdbcTemplate.queryForObject(
			"SELECT count(*) FROM " + table + " WHERE raw_apartment_name = ?",
			Long.class, name
		);
	}

	private static ApartmentTradeData trade(
		String name, boolean canceled, LocalDate cancellationDate
	) {
		return new ApartmentTradeData(
			"11170", "한남동", name, "810", "101동", 2011,
			LocalDate.of(2026, 9, 15), 250_000L,
			new BigDecimal("59.98"), 12, "중개거래",
			canceled, cancellationDate
		);
	}
}
