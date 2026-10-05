package com.geupjido.trade.domain;

import java.math.BigDecimal;
import java.time.LocalDate;

import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

class ApartmentTradeTest {

	@Test
	void 정상_거래를_생성한다() {
		ApartmentTrade trade = new ApartmentTrade(
			LocalDate.of(2026, 9, 15),
			250_000L,
			new BigDecimal("59.98"),
			(short) 12,
			(short) 2011,
			"11170",
			" 한남동 ",
			" 한남테스트아파트 ",
			"810",
			" 101동 ",
			" 중개거래 ",
			false,
			null
		);

		assertThat(trade.getId()).isNull();
		assertThat(trade.getComplex()).isNull();
		assertThat(trade.getZone()).isNull();
		assertThat(trade.getDealDate())
			.isEqualTo(LocalDate.of(2026, 9, 15));
		assertThat(trade.getDealAmountTenThousandWon())
			.isEqualTo(250_000L);
		assertThat(trade.getExclusiveArea())
			.isEqualByComparingTo(new BigDecimal("59.98"));
		assertThat(trade.getFloor()).isEqualTo((short) 12);
		assertThat(trade.getBuildYear()).isEqualTo((short) 2011);
		assertThat(trade.getDistrictCode()).isEqualTo("11170");
		assertThat(trade.getLegalDongName()).isEqualTo("한남동");
		assertThat(trade.getRawApartmentName())
			.isEqualTo("한남테스트아파트");
		assertThat(trade.getLotNumber()).isEqualTo("810");
		assertThat(trade.getApartmentDong()).isEqualTo("101동");
		assertThat(trade.getDealingType()).isEqualTo("중개거래");
		assertThat(trade.isCanceled()).isFalse();
		assertThat(trade.getCancellationDate()).isNull();
	}

	@Test
	void 해제된_거래를_생성한다() {
		ApartmentTrade trade = createTrade(
			true,
			LocalDate.of(2026, 9, 20)
		);

		assertThat(trade.isCanceled()).isTrue();
		assertThat(trade.getCancellationDate())
			.isEqualTo(LocalDate.of(2026, 9, 20));
	}

	@Test
	void 해제된_거래에_해제일이_없으면_예외가_발생한다() {
		assertThatThrownBy(() -> createTrade(true, null))
			.isInstanceOf(IllegalArgumentException.class)
			.hasMessage("해제된 거래에는 해제일이 필요합니다.");
	}

	@Test
	void 정상_거래에_해제일이_있으면_예외가_발생한다() {
		assertThatThrownBy(() ->
			createTrade(false, LocalDate.of(2026, 9, 20))
		)
			.isInstanceOf(IllegalArgumentException.class)
			.hasMessage("정상 거래에는 해제일이 있을 수 없습니다.");
	}

	@Test
	void 해제일이_계약일보다_빠르면_예외가_발생한다() {
		assertThatThrownBy(() ->
			createTrade(true, LocalDate.of(2026, 9, 14))
		)
			.isInstanceOf(IllegalArgumentException.class)
			.hasMessage("해제일은 계약일보다 빠를 수 없습니다.");
	}

	private ApartmentTrade createTrade(
		boolean canceled,
		LocalDate cancellationDate
	) {
		return new ApartmentTrade(
			LocalDate.of(2026, 9, 15),
			150_000L,
			new BigDecimal("84.97"),
			(short) 12,
			(short) 2011,
			"11170",
			"한남동",
			"한남테스트아파트",
			"810",
			"101동",
			"중개거래",
			canceled,
			cancellationDate
		);
	}
}
