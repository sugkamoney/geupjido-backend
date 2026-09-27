package com.geupjido.complex.domain;

import java.time.LocalDate;

import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

class ComplexTest {

	@Test
	void 기본정보로_단지를_생성한다() {
		LocalDate approvalDate = LocalDate.of(2015, 8, 6);

		Complex complex = new Complex(
			"A10027875",
			"괴정 경성스마트W아파트",
			"부산광역시 사하구 괴정동 258",
			"부산광역시 사하구 낙동대로 180",
			182,
			3,
			approvalDate
		);

		assertThat(complex.getId()).isEqualTo("A10027875");
		assertThat(complex.getName())
			.isEqualTo("괴정 경성스마트W아파트");
		assertThat(complex.getAddressJibun())
			.isEqualTo("부산광역시 사하구 괴정동 258");
		assertThat(complex.getAddressRoad())
			.isEqualTo("부산광역시 사하구 낙동대로 180");
		assertThat(complex.getHouseholds()).isEqualTo(182);
		assertThat(complex.getBuildingCount()).isEqualTo(3);
		assertThat(complex.getApprovalDate()).isEqualTo(approvalDate);

		assertThat(complex.getZone()).isNull();
		assertThat((Object) complex.getLocation()).isNull();
		assertThat(complex.getScore()).isNull();
		assertThat(complex.getPrice()).isNull();
		assertThat(complex.isActive()).isFalse();
	}

	@Test
	void 필수_문자열이_비어_있으면_생성할_수_없다() {
		assertThatThrownBy(
			() -> new Complex(
				"A10027875",
				" ",
				"부산광역시 사하구 괴정동 258",
				null,
				182,
				3,
				null
			)
		)
			.isInstanceOf(IllegalArgumentException.class)
			.hasMessageContaining("단지명");
	}

	@Test
	void 세대수가_음수이면_생성할_수_없다() {
		assertThatThrownBy(
			() -> new Complex(
				"A10027875",
				"테스트아파트",
				"부산광역시 사하구 괴정동 258",
				null,
				-1,
				3,
				null
			)
		)
			.isInstanceOf(IllegalArgumentException.class)
			.hasMessageContaining("세대수는 0 이상");
	}

	@Test
	void 동_수가_0이면_생성할_수_없다() {
		assertThatThrownBy(
			() -> new Complex(
				"A10027875",
				"테스트아파트",
				"부산광역시 사하구 괴정동 258",
				null,
				182,
				0,
				null
			)
		)
			.isInstanceOf(IllegalArgumentException.class)
			.hasMessageContaining("동 수는 1 이상");
	}
}
