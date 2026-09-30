package com.geupjido.batch.complex.model;

import java.util.List;
import java.util.Objects;

/**
 * 공동주택 기본정보 일괄 저장 처리 결과를 나타낸다.
 */
public record ComplexBulkImportResult(
	int totalCount,
	int createdCount,
	int skippedCount,
	List<ComplexImportFailure> failures
) {

	public ComplexBulkImportResult {
		if (
			totalCount < 0
				|| createdCount < 0
				|| skippedCount < 0
		) {
			throw new IllegalArgumentException(
				"처리 건수는 0 이상이어야 합니다."
			);
		}

		failures = List.copyOf(
			Objects.requireNonNull(
				failures,
				"실패 목록은 null일 수 없습니다."
			)
		);

		if (
			totalCount
				!= createdCount
				+ skippedCount
				+ failures.size()
		) {
			throw new IllegalArgumentException(
				"전체 건수와 처리 결과 건수의 합이 일치해야 합니다."
			);
		}
	}

	public ComplexBulkImportResult(
		int totalCount,
		int createdCount,
		int skippedCount
	) {
		this(
			totalCount,
			createdCount,
			skippedCount,
			List.of()
		);
	}

	public int failedCount() {
		return failures.size();
	}
}
