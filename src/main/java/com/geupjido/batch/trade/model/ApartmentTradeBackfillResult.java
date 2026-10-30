package com.geupjido.batch.trade.model;

/** 서비스 대상 시군구·계약월의 공공 API 소급 수집 결과. */
public record ApartmentTradeBackfillResult(
	int plannedPairs,
	int alreadyCompletedPairs,
	int processedPairs,
	int failedPairs,
	long sourceTrades,
	long createdTrades,
	long skippedTrades,
	long updatedTrades,
	long conflictTrades
) {
}
