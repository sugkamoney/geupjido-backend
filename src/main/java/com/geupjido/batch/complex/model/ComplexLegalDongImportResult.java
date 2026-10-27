package com.geupjido.batch.complex.model;

import java.util.Objects;
import java.util.regex.Pattern;

/**
 * 법정동 한 곳에서 처리한 단지와 목록 API 실패를 기록한다.
 */
public record ComplexLegalDongImportResult(
	String legalDongCode,
	ComplexBulkImportResult complexes,
	String listFailureReason
) {

	private static final Pattern LEGAL_DONG_CODE_PATTERN =
		Pattern.compile("\\d{10}");

	public ComplexLegalDongImportResult {
		if (
			legalDongCode == null
				|| !LEGAL_DONG_CODE_PATTERN.matcher(legalDongCode).matches()
		) {
			throw new IllegalArgumentException(
				"법정동 코드는 숫자 10자리여야 합니다."
			);
		}

		Objects.requireNonNull(
			complexes,
			"단지 적재 결과는 null일 수 없습니다."
		);

		if (listFailureReason != null) {
			if (listFailureReason.isBlank()) {
				throw new IllegalArgumentException(
					"목록 API 실패 사유는 비어 있을 수 없습니다."
				);
			}

			if (complexes.totalCount() != 0) {
				throw new IllegalArgumentException(
					"목록 API 실패 시 단지 적재 결과는 비어 있어야 합니다."
				);
			}
		}
	}

	public ComplexLegalDongImportStatus status() {
		if (listFailureReason != null) {
			return ComplexLegalDongImportStatus.LIST_FAILED;
		}

		if (complexes.totalCount() == 0) {
			return ComplexLegalDongImportStatus.EMPTY;
		}

		if (complexes.failedCount() > 0) {
			return ComplexLegalDongImportStatus.PARTIAL;
		}

		return ComplexLegalDongImportStatus.SUCCESS;
	}
}
