package com.geupjido.batch.trade.model;

/** 충돌 건을 분리한 월별 소급 적재 결과. */
public record ApartmentTradePartialImportResult(
	ApartmentTradeImportResult imported,
	int conflictRecords,
	int conflictKeys
) {
}
