package com.geupjido.batch.trade.mapper;

import com.geupjido.batch.trade.exception.ApartmentTradeMappingException;
import com.geupjido.batch.trade.model.ApartmentTradeData;
import com.geupjido.trade.domain.ApartmentTrade;
import org.junit.jupiter.api.Test;

import java.math.BigDecimal;
import java.time.LocalDate;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

class ApartmentTradeEntityMapperTest {

	private final ApartmentTradeEntityMapper mapper =
		new ApartmentTradeEntityMapper();

	@Test
	void 정상_거래의_원본_필드와_선택값을_엔티티로_옮긴다() {
		ApartmentTrade trade = mapper.map(data(12, 2011, false, null));

		assertThat(trade.getDistrictCode()).isEqualTo("11170");
		assertThat(trade.getLegalDongName()).isEqualTo("한남동");
		assertThat(trade.getRawApartmentName()).isEqualTo("한남아파트");
		assertThat(trade.getLotNumber()).isEqualTo("810");
		assertThat(trade.getApartmentDong()).isEqualTo("101동");
		assertThat(trade.getBuildYear()).isEqualTo((short) 2011);
		assertThat(trade.getDealDate()).isEqualTo(LocalDate.of(2026, 9, 15));
		assertThat(trade.getDealAmountTenThousandWon()).isEqualTo(250_000L);
		assertThat(trade.getExclusiveArea())
			.isEqualByComparingTo(new BigDecimal("59.98"));
		assertThat(trade.getFloor()).isEqualTo((short) 12);
		assertThat(trade.getDealingType()).isEqualTo("중개거래");
		assertThat(trade.isCanceled()).isFalse();
		assertThat(trade.getCancellationDate()).isNull();
		assertThat(trade.getComplex()).isNull();
		assertThat(trade.getZone()).isNull();
	}

	@Test
	void 선택값이_없거나_해제된_거래도_변환한다() {
		ApartmentTradeData data = new ApartmentTradeData(
			"11170", "한남동", "한남아파트", null, null,
			null, LocalDate.of(2026, 9, 15), 250_000L,
			null, null, null, true, LocalDate.of(2026, 9, 20)
		);

		ApartmentTrade trade = mapper.map(data);

		assertThat(trade.getLotNumber()).isNull();
		assertThat(trade.getApartmentDong()).isNull();
		assertThat(trade.getBuildYear()).isNull();
		assertThat(trade.getExclusiveArea()).isNull();
		assertThat(trade.getFloor()).isNull();
		assertThat(trade.getDealingType()).isNull();
		assertThat(trade.isCanceled()).isTrue();
		assertThat(trade.getCancellationDate())
			.isEqualTo(LocalDate.of(2026, 9, 20));
	}

	@Test
	void 층이_Short_범위를_벗어나면_실패한다() {
		assertThatThrownBy(() -> mapper.map(data(32_768, 2011, false, null)))
			.isInstanceOf(ApartmentTradeMappingException.class)
			.hasMessageContaining("층");
	}

	@Test
	void 건축연도가_Short_범위를_벗어나면_실패한다() {
		assertThatThrownBy(() -> mapper.map(data(12, 32_768, false, null)))
			.isInstanceOf(ApartmentTradeMappingException.class)
			.hasMessageContaining("건축연도");
	}

	@Test
	void 해제_정보가_서로_맞지_않으면_엔티티_검증에_실패한다() {
		assertThatThrownBy(() -> mapper.map(data(12, 2011, true, null)))
			.isInstanceOf(IllegalArgumentException.class)
			.hasMessageContaining("해제일");
	}

	private ApartmentTradeData data(
		Integer floor,
		Integer buildYear,
		boolean canceled,
		LocalDate cancellationDate
	) {
		return new ApartmentTradeData(
			"11170", "한남동", "한남아파트", "810", "101동",
			buildYear, LocalDate.of(2026, 9, 15), 250_000L,
			new BigDecimal("59.98"), floor, "중개거래",
			canceled, cancellationDate
		);
	}
}
