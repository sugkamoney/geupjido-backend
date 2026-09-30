package com.geupjido.batch.complex.model;

import java.util.ArrayList;
import java.util.List;

import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

class ComplexBulkImportResultTest {

	@Test
	void 성공_건수와_실패_건수를_집계한다() {
		ComplexImportFailure failure =
			new ComplexImportFailure(
				"A10000002",
				ComplexImportFailureType.API,
				"API 호출에 실패했습니다."
			);

		ComplexBulkImportResult result =
			new ComplexBulkImportResult(
				3,
				1,
				1,
				List.of(failure)
			);

		assertThat(result.totalCount()).isEqualTo(3);
		assertThat(result.createdCount()).isEqualTo(1);
		assertThat(result.skippedCount()).isEqualTo(1);
		assertThat(result.failedCount()).isEqualTo(1);
		assertThat(result.failures()).containsExactly(failure);
	}

	@Test
	void 실패_목록은_외부_변경의_영향을_받지_않는다() {
		ComplexImportFailure failure =
			new ComplexImportFailure(
				"A10000002",
				ComplexImportFailureType.MAPPING,
				"단지 정보 변환에 실패했습니다."
			);

		List<ComplexImportFailure> failures =
			new ArrayList<>();
		failures.add(failure);

		ComplexBulkImportResult result =
			new ComplexBulkImportResult(
				1,
				0,
				0,
				failures
			);

		failures.clear();

		assertThat(result.failures())
			.containsExactly(failure);

		assertThatThrownBy(
			() -> result.failures().clear()
		)
			.isInstanceOf(UnsupportedOperationException.class);
	}

	@Test
	void 전체_건수와_처리_결과의_합이_다르면_생성할_수_없다() {
		assertThatThrownBy(
			() -> new ComplexBulkImportResult(
				3,
				1,
				1,
				List.of()
			)
		)
			.isInstanceOf(IllegalArgumentException.class)
			.hasMessage(
				"전체 건수와 처리 결과 건수의 합이 일치해야 합니다."
			);
	}

	@Test
	void 처리_건수가_음수이면_생성할_수_없다() {
		assertThatThrownBy(
			() -> new ComplexBulkImportResult(
				-1,
				0,
				0,
				List.of()
			)
		)
			.isInstanceOf(IllegalArgumentException.class)
			.hasMessage(
				"처리 건수는 0 이상이어야 합니다."
			);
	}
}
