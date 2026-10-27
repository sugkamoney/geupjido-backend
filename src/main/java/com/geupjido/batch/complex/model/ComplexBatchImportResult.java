package com.geupjido.batch.complex.model;

import java.util.HashSet;
import java.util.List;
import java.util.Objects;
import java.util.Set;

/**
 * 여러 법정동에 대한 공동주택 단지 적재 결과를 모은다.
 */
public record ComplexBatchImportResult(
	List<ComplexLegalDongImportResult> legalDongs
) {

	public ComplexBatchImportResult {
		legalDongs = List.copyOf(
			Objects.requireNonNull(
				legalDongs,
				"법정동 적재 결과는 null일 수 없습니다."
			)
		);

		Set<String> uniqueCodes = new HashSet<>();
		for (ComplexLegalDongImportResult legalDong : legalDongs) {
			if (!uniqueCodes.add(legalDong.legalDongCode())) {
				throw new IllegalArgumentException(
					"중복된 법정동 적재 결과가 있습니다: "
						+ legalDong.legalDongCode()
				);
			}
		}
	}

	public long count(ComplexLegalDongImportStatus status) {
		return legalDongs.stream()
			.filter(legalDong -> legalDong.status() == status)
			.count();
	}

	public int totalComplexCount() {
		return legalDongs.stream()
			.mapToInt(legalDong -> legalDong.complexes().totalCount())
			.sum();
	}

	public int createdComplexCount() {
		return legalDongs.stream()
			.mapToInt(legalDong -> legalDong.complexes().createdCount())
			.sum();
	}

	public int skippedComplexCount() {
		return legalDongs.stream()
			.mapToInt(legalDong -> legalDong.complexes().skippedCount())
			.sum();
	}

	public int failedComplexCount() {
		return legalDongs.stream()
			.mapToInt(legalDong -> legalDong.complexes().failedCount())
			.sum();
	}

	public List<String> retryLegalDongCodes() {
		return legalDongs.stream()
			.filter(legalDong ->
				legalDong.status() == ComplexLegalDongImportStatus.PARTIAL
					|| legalDong.status()
						== ComplexLegalDongImportStatus.LIST_FAILED
			)
			.map(ComplexLegalDongImportResult::legalDongCode)
			.toList();
	}
}
