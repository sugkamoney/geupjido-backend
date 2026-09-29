package com.geupjido.batch.complex.model;

/**
 * 공동주택 기본정보 일괄 저장 처리 결과를 나타낸다.
 */
public record ComplexBulkImportResult(
	int totalCount,
	int createdCount,
	int skippedCount
) {
}
