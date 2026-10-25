package com.geupjido.batch.trade.service;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.List;
import java.util.UUID;

import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.condition.EnabledIfEnvironmentVariable;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.test.context.ActiveProfiles;

import com.geupjido.batch.trade.exception.ApartmentTradeMappingException;
import com.geupjido.batch.trade.model.ApartmentTradeData;
import com.geupjido.batch.trade.model.ApartmentTradeImportResult;
import com.geupjido.trade.domain.ApartmentTrade;
import com.geupjido.trade.repository.ApartmentTradeRepository;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

@EnabledIfEnvironmentVariable(
	named = "RUN_POSTGIS_IT",
	matches = "true"
)
@SpringBootTest
@ActiveProfiles("local")
class ApartmentTradePersistenceServiceIT {

	private final String testPrefix =
		"IT76-" + UUID.randomUUID().toString().substring(0, 8);

	@Autowired
	private ApartmentTradePersistenceService service;

	@Autowired
	private ApartmentTradeRepository repository;

	@Autowired
	private JdbcTemplate jdbcTemplate;

	@AfterEach
	void deleteTestTrades() {
		jdbcTemplate.update(
			"DELETE FROM apartment_trade WHERE raw_apartment_name LIKE ?",
			testPrefix + "%"
		);
	}

	@Test
	void 신규_저장_후_같은_거래를_재수집하면_건너뛴다() {
		String name = testPrefix + "-A";
		ApartmentTradeData data = trade(name, false, null);

		ApartmentTradeImportResult first = service.persist(List.of(data));
		ApartmentTradeImportResult second = service.persist(List.of(data));

		assertThat(first).isEqualTo(
			new ApartmentTradeImportResult(1, 1, 0, 0)
		);
		assertThat(second).isEqualTo(
			new ApartmentTradeImportResult(1, 0, 1, 0)
		);
		assertThat(find(name).isCanceled()).isFalse();
		assertThat(count(name)).isEqualTo(1);
	}

	@Test
	void 정상_거래를_재수집한_해제_상태로_갱신한다() {
		String name = testPrefix + "-A";
		LocalDate cancellationDate = LocalDate.of(2026, 9, 20);
		service.persist(List.of(trade(name, false, null)));

		ApartmentTradeImportResult updated = service.persist(
			List.of(trade(name, true, cancellationDate))
		);

		assertThat(updated).isEqualTo(
			new ApartmentTradeImportResult(1, 0, 0, 1)
		);
		assertThat(find(name).isCanceled()).isTrue();
		assertThat(find(name).getCancellationDate())
			.isEqualTo(cancellationDate);
		assertThat(count(name)).isEqualTo(1);
	}

	@Test
	void 충돌이_나면_앞선_신규_저장과_해제_갱신을_모두_롤백한다() {
		String existingName = testPrefix + "-A";
		String newName = testPrefix + "-B";
		service.persist(List.of(trade(existingName, false, null)));

		assertThatThrownBy(() -> service.persist(List.of(
			trade(newName, false, null),
			trade(existingName, true, LocalDate.of(2026, 9, 20)),
			trade(existingName, false, null)
		)))
			.isInstanceOf(IllegalStateException.class)
			.hasMessageContaining("해제된 거래");

		assertThat(count(newName)).isZero();
		assertThat(find(existingName).isCanceled()).isFalse();
	}

	@Test
	void 다른_해제일이_오면_기존_해제일을_유지한다() {
		String name = testPrefix + "-A";
		LocalDate originalDate = LocalDate.of(2026, 9, 20);
		service.persist(List.of(trade(name, true, originalDate)));

		assertThatThrownBy(() -> service.persist(List.of(
			trade(name, true, LocalDate.of(2026, 9, 21))
		)))
			.isInstanceOf(IllegalStateException.class)
			.hasMessageContaining("해제일");

		assertThat(find(name).getCancellationDate())
			.isEqualTo(originalDate);
	}

	@Test
	void 변환_실패도_같은_월의_저장을_롤백한다() {
		String validName = testPrefix + "-A";
		ApartmentTradeData invalid = new ApartmentTradeData(
			"11170", "한남동", testPrefix + "-B", "810", "101동",
			2011, LocalDate.of(2026, 9, 15), 250_000L,
			new BigDecimal("59.98"), 32_768, "중개거래",
			false, null
		);

		assertThatThrownBy(() -> service.persist(List.of(
			trade(validName, false, null), invalid
		)))
			.isInstanceOf(ApartmentTradeMappingException.class);

		assertThat(count(validName)).isZero();
	}

	@Test
	void 전용면적_소수_6자리를_보존하고_서로_다른_거래를_구분한다() {
		String name = testPrefix + "-A";
		ApartmentTradeData first = trade(
			name, false, null, new BigDecimal("59.123456")
		);
		ApartmentTradeData second = trade(
			name, false, null, new BigDecimal("59.123457")
		);

		ApartmentTradeImportResult created =
			service.persist(List.of(first, second));
		ApartmentTradeImportResult repeated =
			service.persist(List.of(first, second));

		assertThat(created).isEqualTo(
			new ApartmentTradeImportResult(2, 2, 0, 0)
		);
		assertThat(repeated).isEqualTo(
			new ApartmentTradeImportResult(2, 0, 2, 0)
		);
		assertThat(count(name)).isEqualTo(2);
		assertThat(find(name, new BigDecimal("59.123456"))
			.getExclusiveArea())
			.isEqualByComparingTo(new BigDecimal("59.123456"));
		assertThat(find(name, new BigDecimal("59.123457"))
			.getExclusiveArea())
			.isEqualByComparingTo(new BigDecimal("59.123457"));
	}

	private ApartmentTradeData trade(
		String name,
		boolean canceled,
		LocalDate cancellationDate
	) {
		return trade(
			name, canceled, cancellationDate, new BigDecimal("59.98")
		);
	}

	private ApartmentTradeData trade(
		String name,
		boolean canceled,
		LocalDate cancellationDate,
		BigDecimal exclusiveArea
	) {
		return new ApartmentTradeData(
			"11170", "한남동", name, "810", "101동",
			2011, LocalDate.of(2026, 9, 15), 250_000L,
			exclusiveArea, 12, "중개거래",
			canceled, cancellationDate
		);
	}

	private ApartmentTrade find(String name) {
		return find(name, new BigDecimal("59.98"));
	}

	private ApartmentTrade find(String name, BigDecimal exclusiveArea) {
		return repository.findBySourceKey(
			"11170", "한남동", name, LocalDate.of(2026, 9, 15),
			exclusiveArea, (short) 12, 250_000L
		).orElseThrow();
	}

	private long count(String name) {
		return jdbcTemplate.queryForObject(
			"SELECT COUNT(*) FROM apartment_trade WHERE raw_apartment_name = ?",
			Long.class, name
		);
	}
}
