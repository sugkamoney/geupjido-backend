package com.geupjido.batch.trade.model;

/** 단지 대표 거래와 가격 기준 잠정 순위의 재계산 결과. */
public record ComplexRepresentativePriceResult(
	int totalComplexCount,
	int pricedComplexCount,
	int rankedComplexCount,
	int withoutEligibleTradeCount
) {
}
