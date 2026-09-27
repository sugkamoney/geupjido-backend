package com.geupjido.batch.complex.mapper;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.time.format.DateTimeParseException;

import org.junit.jupiter.api.Test;

import com.geupjido.batch.complex.dto.ComplexBasicInfoApiItem;
import com.geupjido.batch.complex.exception.ComplexBasicInfoMappingException;
import com.geupjido.complex.domain.Complex;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

class ComplexBasicInfoMapperTest {

	private final ComplexBasicInfoMapper mapper =
		new ComplexBasicInfoMapper();

	@Test
	void 공동주택_기본정보를_단지로_변환한다() {
		ComplexBasicInfoApiItem item =
			new ComplexBasicInfoApiItem(
				"A10027875",
				"괴정 경성스마트W아파트",
				"부산광역시 사하구 괴정동 258",
				"부산광역시 사하구 낙동대로 180",
				"3",
				new BigDecimal("182.0"),
				"20150806",
				"2638010100"
			);

		Complex complex = mapper.map(item);

		assertThat(complex.getId()).isEqualTo("A10027875");
		assertThat(complex.getName())
			.isEqualTo("괴정 경성스마트W아파트");
		assertThat(complex.getAddressJibun())
			.isEqualTo("부산광역시 사하구 괴정동 258");
		assertThat(complex.getAddressRoad())
			.isEqualTo("부산광역시 사하구 낙동대로 180");
		assertThat(complex.getHouseholds()).isEqualTo(182);
		assertThat(complex.getBuildingCount()).isEqualTo(3);
		assertThat(complex.getApprovalDate())
			.isEqualTo(LocalDate.of(2015, 8, 6));
		assertThat(complex.isActive()).isFalse();
	}

	@Test
	void 선택_정보가_비어_있으면_null로_변환한다() {
		ComplexBasicInfoApiItem item =
			new ComplexBasicInfoApiItem(
				"A10027875",
				"괴정 경성스마트W아파트",
				"부산광역시 사하구 괴정동 258",
				" ",
				" ",
				new BigDecimal("182"),
				" ",
				"2638010100"
			);

		Complex complex = mapper.map(item);

		assertThat(complex.getAddressRoad()).isNull();
		assertThat(complex.getBuildingCount()).isNull();
		assertThat(complex.getApprovalDate()).isNull();
	}

	@Test
	void 세대수가_없으면_변환할_수_없다() {
		ComplexBasicInfoApiItem item =
			new ComplexBasicInfoApiItem(
				"A10027875",
				"괴정 경성스마트W아파트",
				"부산광역시 사하구 괴정동 258",
				null,
				"3",
				null,
				"20150806",
				"2638010100"
			);

		assertThatThrownBy(() -> mapper.map(item))
			.isInstanceOf(
				ComplexBasicInfoMappingException.class
			)
			.hasMessageContaining("세대수가 없습니다");
	}

	@Test
	void 세대수에_소수값이_있으면_변환할_수_없다() {
		ComplexBasicInfoApiItem item =
			new ComplexBasicInfoApiItem(
				"A10027875",
				"괴정 경성스마트W아파트",
				"부산광역시 사하구 괴정동 258",
				null,
				"3",
				new BigDecimal("182.5"),
				"20150806",
				"2638010100"
			);

		assertThatThrownBy(() -> mapper.map(item))
			.isInstanceOf(
				ComplexBasicInfoMappingException.class
			)
			.hasMessageContaining(
				"세대수를 정수로 변환할 수 없습니다"
			)
			.hasCauseInstanceOf(ArithmeticException.class);
	}

	@Test
	void 동_수가_숫자가_아니면_변환할_수_없다() {
		ComplexBasicInfoApiItem item =
			new ComplexBasicInfoApiItem(
				"A10027875",
				"괴정 경성스마트W아파트",
				"부산광역시 사하구 괴정동 258",
				null,
				"세 개 동",
				new BigDecimal("182"),
				"20150806",
				"2638010100"
			);

		assertThatThrownBy(() -> mapper.map(item))
			.isInstanceOf(
				ComplexBasicInfoMappingException.class
			)
			.hasMessageContaining(
				"동 수를 정수로 변환할 수 없습니다"
			)
			.hasCauseInstanceOf(NumberFormatException.class);
	}

	@Test
	void 사용승인일_형식이_잘못되면_변환할_수_없다() {
		ComplexBasicInfoApiItem item =
			new ComplexBasicInfoApiItem(
				"A10027875",
				"괴정 경성스마트W아파트",
				"부산광역시 사하구 괴정동 258",
				null,
				"3",
				new BigDecimal("182"),
				"2015-08-06",
				"2638010100"
			);

		assertThatThrownBy(() -> mapper.map(item))
			.isInstanceOf(
				ComplexBasicInfoMappingException.class
			)
			.hasMessageContaining(
				"사용승인일을 날짜로 변환할 수 없습니다"
			)
			.hasCauseInstanceOf(DateTimeParseException.class);
	}

	@Test
	void 기본정보가_없으면_변환할_수_없다() {
		assertThatThrownBy(() -> mapper.map(null))
			.isInstanceOf(
				ComplexBasicInfoMappingException.class
			)
			.hasMessageContaining(
				"변환할 공동주택 기본정보가 없습니다"
			);
	}
}
