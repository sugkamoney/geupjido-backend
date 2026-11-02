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
import com.geupjido.batch.trade.exception.ApartmentTradeMappingException;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

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
	void 요청과_응답의_시군구가_다르면_저장하지_않고_매핑_실패로_분류한다() {
		String name = "IT106-" + UUID.randomUUID().toString().substring(0, 8);

		assertThatThrownBy(() -> service.persist("11110", "202609",
			List.of(trade(name, false, null))))
			.isInstanceOf(ApartmentTradeMappingException.class)
			.hasMessageContaining("시군구 또는 계약월");
		assertThat(count("apartment_trade", name)).isZero();
	}

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
		assertThat(first.newConflictRecords()).isEqualTo(2);
		assertThat(second.imported().skippedCount()).isEqualTo(1);
		assertThat(second.newConflictRecords()).isZero();
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
		assertThat(result.newConflictRecords()).isEqualTo(1);
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

	@Test
	void 같은_응답_순서에서_해제일이_바뀌면_새_충돌로_기록한다() {
		String name = "IT106-" + UUID.randomUUID().toString().substring(0, 8);
		ApartmentTradeData normal = trade(name, false, null);
		var first = service.persist("11170", "202609", List.of(
			normal, trade(name, true, LocalDate.of(2026, 9, 20))));
		var changed = service.persist("11170", "202609", List.of(
			normal, trade(name, true, LocalDate.of(2026, 9, 21))));
		var repeated = service.persist("11170", "202609", List.of(
			normal, trade(name, true, LocalDate.of(2026, 9, 21))));

		assertThat(first.newConflictRecords()).isEqualTo(2);
		assertThat(changed.newConflictRecords()).isEqualTo(1);
		assertThat(repeated.newConflictRecords()).isZero();
	}

	@Test
	void API_응답_순서만_바뀌면_새_충돌로_세지_않는다() {
		String prefix = "IT106-" + UUID.randomUUID().toString().substring(0, 8);
		ApartmentTradeData normalA = trade(prefix + "-A", false, null);
		ApartmentTradeData canceledA = trade(prefix + "-A", true,
			LocalDate.of(2026, 9, 20));
		ApartmentTradeData normalB = trade(prefix + "-B", false, null);
		ApartmentTradeData canceledB = trade(prefix + "-B", true,
			LocalDate.of(2026, 9, 21));

		var first = service.persist("11170", "202609", List.of(
			normalA, canceledA, normalB, canceledB));
		var reordered = service.persist("11170", "202609", List.of(
			normalB, canceledB, normalA, canceledA));

		assertThat(first.newConflictRecords()).isEqualTo(4);
		assertThat(reordered.newConflictRecords()).isZero();
		assertThat(count("apartment_trade_conflict_seen", prefix + "-A"))
			.isEqualTo(2);
		assertThat(count("apartment_trade_conflict_seen", prefix + "-B"))
			.isEqualTo(2);
		assertThat(jdbcTemplate.queryForObject("""
			SELECT count(*) FROM apartment_trade_conflict
			WHERE raw_apartment_name IN (?, ?) AND review_status = 'OPEN'
			""", Long.class, prefix + "-A", prefix + "-B"))
			.isEqualTo(4);
		assertThat(jdbcTemplate.queryForObject("""
			SELECT count(*) FROM apartment_trade_conflict c
			JOIN apartment_trade_conflict_seen s ON s.id = c.source_seen_id
			WHERE c.raw_apartment_name IN (?, ?) AND c.review_status = 'OPEN'
			  AND c.raw_apartment_name = s.raw_apartment_name
			  AND c.is_canceled = s.is_canceled
			""", Long.class, prefix + "-A", prefix + "-B"))
			.isEqualTo(4);
	}

	@Test
	void 해제_뒤_등기된_정상_원본은_별도_거래로_보존한다() {
		String name = "IT100-" + UUID.randomUUID().toString().substring(0, 8);
		LocalDate registrationDate = LocalDate.of(2026, 10, 15);

		List<ApartmentTradeData> source = List.of(
			trade(name, false, null, registrationDate),
			trade(name, true, LocalDate.of(2026, 10, 8))
		);
		var result = service.persist("11170", "202609", source);
		var repeated = service.persist("11170", "202609", source);

		assertThat(result.conflictRecords()).isZero();
		assertThat(result.imported().createdCount()).isEqualTo(2);
		assertThat(repeated.imported().skippedCount()).isEqualTo(2);
		assertThat(count("apartment_trade", name)).isEqualTo(2);
		assertThat(jdbcTemplate.queryForObject("""
			SELECT count(*) FROM apartment_trade
			WHERE raw_apartment_name = ? AND is_canceled = false
			""", Long.class, name)).isEqualTo(1);
		assertThat(jdbcTemplate.queryForObject("""
			SELECT registration_date FROM apartment_trade
			WHERE raw_apartment_name = ? AND is_canceled = false
			""", LocalDate.class, name)).isEqualTo(registrationDate);
	}

	@Test
	void 동_정보가_없는_정상_거래는_등기일만으로_해제_거래와_분리하지_않는다() {
		String name = "IT100-" + UUID.randomUUID().toString().substring(0, 8);
		LocalDate registrationDate = LocalDate.of(2026, 10, 15);
		service.persist("11170", "202609", List.of(
			new ApartmentTradeData(
				"11170", "한남동", name, "810", null, 2011,
				LocalDate.of(2026, 9, 15), 250_000L,
				new BigDecimal("59.98"), 12, "중개거래",
				false, null, registrationDate
			)
		));

		var result = service.persist("11170", "202609", List.of(
			trade(name, true, LocalDate.of(2026, 10, 8))
		));

		assertThat(result.conflictRecords()).isEqualTo(1);
		assertThat(jdbcTemplate.queryForObject("""
			SELECT conflict_reason FROM apartment_trade_conflict
			WHERE raw_apartment_name = ?
			""", String.class, name))
			.isEqualTo("CANCELED_BEFORE_REGISTRATION");
		assertThat(jdbcTemplate.queryForObject("""
			SELECT is_canceled FROM apartment_trade
			WHERE raw_apartment_name = ?
			""", Boolean.class, name)).isFalse();
	}

	@Test
	void 응답_순서가_바뀌어도_분리된_정상_거래를_저장하고_검토_행을_닫는다() {
		String name = "IT100-" + UUID.randomUUID().toString().substring(0, 8);
		LocalDate cancellation = LocalDate.of(2026, 10, 8);
		List<ApartmentTradeData> unresolved = List.of(
			trade(name, false, null), trade(name, true, cancellation)
		);
		service.persist("11170", "202609", List.of(
			trade(name, true, cancellation)
		));
		service.persist("11170", "202609", unresolved);

		var resolved = service.persist("11170", "202609", List.of(
			trade(name + "-other", false, null),
			trade(name, true, cancellation),
			trade(name, false, null, LocalDate.of(2026, 10, 15))
		));

		assertThat(resolved.conflictRecords()).isZero();
		assertThat(resolved.imported().createdCount()).isEqualTo(2);
		assertThat(count("apartment_trade", name)).isEqualTo(2);
		assertThat(jdbcTemplate.queryForObject("""
			SELECT count(*) FROM apartment_trade_conflict
			WHERE raw_apartment_name = ? AND review_status = 'OPEN'
			""", Long.class, name)).isZero();
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
		return trade(name, canceled, cancellationDate, null);
	}

	private static ApartmentTradeData trade(
		String name, boolean canceled, LocalDate cancellationDate,
		LocalDate registrationDate
	) {
		return new ApartmentTradeData(
			"11170", "한남동", name, "810", "101동", 2011,
			LocalDate.of(2026, 9, 15), 250_000L,
			new BigDecimal("59.98"), 12, "중개거래",
			canceled, cancellationDate, registrationDate
		);
	}
}
