package com.geupjido.batch.trade.model;

/**
 * 한 시군구·계약월의 실거래가 저장 결과를 나타낸다.
 */
public record ApartmentTradeImportResult(
	int totalCount,
	int createdCount,
	int skippedCount,
	int updatedCount
) {
	public ApartmentTradeImportResult {
		if (
			totalCount < 0
				|| createdCount < 0
				|| skippedCount < 0
				|| updatedCount < 0
		) {
			throw new IllegalArgumentException(
				"처리 건수는 0 이상이어야 합니다."
			);
		}

		if (totalCount != createdCount + skippedCount + updatedCount) {
			throw new IllegalArgumentException(
				"전체 건수와 처리 결과 건수의 합이 일치해야 합니다."
			);
		}
	}
}
