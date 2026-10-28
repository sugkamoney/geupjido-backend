package com.geupjido.batch.trade.model;

import java.util.Map;

import com.geupjido.batch.trade.model.ApartmentTradeCandidateAssessment.Status;

/** 기존 거래 전체의 서비스 범위와 연결 상태를 점검한 집계다. */
public record ApartmentTradeAuditResult(
	long totalCount,
	long inScopeCount,
	long outOfScopeCount,
	long unknownLegalDongCount,
	long linkedCount,
	long inconsistentLinkCount,
	Map<Status, Long> unlinkedCandidateCounts
) {
}
