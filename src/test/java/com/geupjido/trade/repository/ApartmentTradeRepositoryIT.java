package com.geupjido.trade.repository;

import java.math.BigDecimal;
import java.time.LocalDate;

import jakarta.persistence.EntityManager;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.condition.EnabledIfEnvironmentVariable;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.transaction.annotation.Transactional;

import com.geupjido.trade.domain.ApartmentTrade;

import static org.assertj.core.api.Assertions.assertThat;

@EnabledIfEnvironmentVariable(
	named = "RUN_POSTGIS_IT",
	matches = "true"
)
@SpringBootTest
@ActiveProfiles("local")
@Transactional
class ApartmentTradeRepositoryIT {

	@Autowired
	private ApartmentTradeRepository apartmentTradeRepository;

	@Autowired
	private EntityManager entityManager;

	@Test
	void 실거래가를_저장하고_조회한다() {
		ApartmentTrade trade = new ApartmentTrade(
			LocalDate.of(2026, 9, 15),
			250_000L,
			new BigDecimal("59.98"),
			(short) 12,
			(short) 2011,
			"11170",
			"한남동",
			"통합테스트아파트",
			"810",
			"101동",
			"중개거래",
			false,
			null
		);

		ApartmentTrade saved =
			apartmentTradeRepository.saveAndFlush(trade);
		Long savedId = saved.getId();

		entityManager.clear();

		ApartmentTrade found = apartmentTradeRepository
			.findById(savedId)
			.orElseThrow();

		assertThat(found.getId()).isEqualTo(savedId);
		assertThat(found.getDealDate())
			.isEqualTo(LocalDate.of(2026, 9, 15));
		assertThat(found.getDealAmountTenThousandWon())
			.isEqualTo(250_000L);
		assertThat(found.getExclusiveArea())
			.isEqualByComparingTo(new BigDecimal("59.98"));
		assertThat(found.getDistrictCode()).isEqualTo("11170");
		assertThat(found.getLegalDongName()).isEqualTo("한남동");
		assertThat(found.getRawApartmentName())
			.isEqualTo("통합테스트아파트");
		assertThat(found.isCanceled()).isFalse();
		assertThat(found.getCancellationDate()).isNull();
		assertThat(found.getComplex()).isNull();
		assertThat(found.getZone()).isNull();
		assertThat(found.getCreatedAt()).isNotNull();

		assertThat(apartmentTradeRepository.findBySourceKey(
			"11170", "한남동", "통합테스트아파트",
			LocalDate.of(2026, 9, 15), new BigDecimal("59.98"),
			(short) 12, 250_000L
		)).hasValueSatisfying(sourceTrade ->
			assertThat(sourceTrade.getId()).isEqualTo(savedId)
		);
	}

	@Test
	void 선택값이_null인_거래도_원본_키로_조회한다() {
		ApartmentTrade saved = apartmentTradeRepository.saveAndFlush(
			new ApartmentTrade(
				LocalDate.of(2026, 9, 16), 260_000L,
				null, null, null, "11170", "한남동",
				"선택값없는아파트", null, null, null,
				false, null
			)
		);

		entityManager.clear();

		assertThat(apartmentTradeRepository.findBySourceKey(
			"11170", "한남동", "선택값없는아파트",
			LocalDate.of(2026, 9, 16), null, null, 260_000L
		)).hasValueSatisfying(sourceTrade ->
			assertThat(sourceTrade.getId()).isEqualTo(saved.getId())
		);
	}
}
