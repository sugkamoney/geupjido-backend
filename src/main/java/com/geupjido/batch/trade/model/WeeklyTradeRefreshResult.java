package com.geupjido.batch.trade.model;

import com.geupjido.batch.trade.service.ApartmentTradeExactLinkService;

/** 주간 실거래 수집·단지 연결·대표 시세 갱신의 전체 결과. */
public record WeeklyTradeRefreshResult(
	WeeklyTradeCollectionResult collection,
	ApartmentTradeExactLinkService.Result link,
	ComplexRepresentativePriceResult price,
	Status status
) {
	public enum Status {
		COMPLETE, COMPLETE_WITH_CONFLICTS, NEEDS_REVIEW, FAILED
	}
}
