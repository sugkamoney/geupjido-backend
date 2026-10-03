package com.geupjido.batch.trade.mapper;

import java.math.BigDecimal;
import java.time.LocalDate;

import com.geupjido.batch.trade.exception.ApartmentTradeMappingException;
import org.junit.jupiter.api.Test;

import com.geupjido.batch.trade.dto.ApartmentTradeApiItem;
import com.geupjido.batch.trade.model.ApartmentTradeData;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.AssertionsForClassTypes.assertThatThrownBy;

class ApartmentTradeMapperTest {

	private final ApartmentTradeMapper mapper =
		new ApartmentTradeMapper();

	@Test
	void 정상_거래를_내부_모델로_변환한다() {
		ApartmentTradeApiItem item =
			new ApartmentTradeApiItem(
				"101",
				" 한남테스트아파트 ",
				"2011",
				null,
				null,
				null,
				"250,000",
				"15",
				"9",
				"2026",
				"중개거래",
				null,
				"59.98",
				"12",
				"810",
				null,
				null,
				"11170",
				null,
				"한남동"
			);

		ApartmentTradeData result = mapper.map(item);

		assertThat(result.districtCode()).isEqualTo("11170");
		assertThat(result.legalDongName()).isEqualTo("한남동");
		assertThat(result.apartmentName())
			.isEqualTo("한남테스트아파트");
		assertThat(result.lotNumber()).isEqualTo("810");
		assertThat(result.apartmentDong()).isEqualTo("101");
		assertThat(result.buildYear()).isEqualTo(2011);
		assertThat(result.dealDate())
			.isEqualTo(LocalDate.of(2026, 9, 15));
		assertThat(result.dealAmountTenThousandWon())
			.isEqualTo(250_000L);
		assertThat(result.exclusiveArea())
			.isEqualByComparingTo(new BigDecimal("59.98"));
		assertThat(result.floor()).isEqualTo(12);
		assertThat(result.dealingType()).isEqualTo("중개거래");
		assertThat(result.canceled()).isFalse();
		assertThat(result.cancellationDate()).isNull();
	}

	@Test
	void 해제된_거래를_해제일과_함께_변환한다() {
		ApartmentTradeApiItem item =
			new ApartmentTradeApiItem(
				"101",
				"한남테스트아파트",
				"2011",
				null,
				"26.09.20",
				"O",
				"250,000",
				"15",
				"9",
				"2026",
				"중개거래",
				null,
				"59.98",
				"12",
				"810",
				null,
				null,
				"11170",
				null,
				"한남동"
			);

		ApartmentTradeData result = mapper.map(item);

		assertThat(result.canceled()).isTrue();
		assertThat(result.cancellationDate())
			.isEqualTo(LocalDate.of(2026, 9, 20));
	}

	@Test
	void 변환할_거래가_없으면_예외가_발생한다() {
		assertThatThrownBy(() -> mapper.map(null))
			.isInstanceOf(ApartmentTradeMappingException.class)
			.hasMessageContaining(
				"변환할 아파트 매매 실거래가가 없습니다."
			);
	}

	@Test
	void 거래금액이_숫자가_아니면_예외가_발생한다() {
		ApartmentTradeApiItem item =
			createItem("금액없음", null, null);

		assertThatThrownBy(() -> mapper.map(item))
			.isInstanceOf(ApartmentTradeMappingException.class)
			.hasMessageContaining(
				"거래금액을 숫자로 변환할 수 없습니다"
			);
	}

	@Test
	void 해제된_거래에_해제일이_없으면_예외가_발생한다() {
		ApartmentTradeApiItem item =
			createItem("250,000", null, "O");

		assertThatThrownBy(() -> mapper.map(item))
			.isInstanceOf(ApartmentTradeMappingException.class)
			.hasMessageContaining(
				"해제된 거래에는 해제사유발생일이 필요합니다."
			);
	}

	@Test
	void 정상_거래에_해제일이_있으면_예외가_발생한다() {
		ApartmentTradeApiItem item =
			createItem("250,000", "26.09.20", null);

		assertThatThrownBy(() -> mapper.map(item))
			.isInstanceOf(ApartmentTradeMappingException.class)
			.hasMessageContaining(
				"정상 거래에는 해제사유발생일이 있을 수 없습니다."
			);
	}

	private ApartmentTradeApiItem createItem(
		String dealAmount,
		String cancellationDate,
		String cancellationType
	) {
		return new ApartmentTradeApiItem(
			"101",
			"한남테스트아파트",
			"2011",
			null,
			cancellationDate,
			cancellationType,
			dealAmount,
			"15",
			"9",
			"2026",
			"중개거래",
			null,
			"59.98",
			"12",
			"810",
			null,
			null,
			"11170",
			null,
			"한남동"
		);
	}
}
