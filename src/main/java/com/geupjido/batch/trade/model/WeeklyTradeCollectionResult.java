package com.geupjido.batch.trade.model;

import java.util.List;

/** 주간 수집에서 시군구·계약월별로 확인한 결과. */
public record WeeklyTradeCollectionResult(List<PairResult> pairs) {

	public WeeklyTradeCollectionResult {
		pairs = List.copyOf(pairs);
	}

	public long count(Status status) {
		return pairs.stream().filter(pair -> pair.status() == status).count();
	}

	public long conflictTrades() {
		return pairs.stream().mapToLong(PairResult::conflictTrades).sum();
	}

	public long newConflictTrades() {
		return pairs.stream().mapToLong(PairResult::newConflictTrades).sum();
	}

	public enum Status {
		SUCCESS, PARTIAL, FAILED
	}

	public record PairResult(
		String districtCode,
		String dealYearMonth,
		Status status,
		int sourceTrades,
		int createdTrades,
		int skippedTrades,
		int updatedTrades,
		int conflictTrades,
		String failureReason,
		int newConflictTrades
	) {
		public PairResult(
			String districtCode, String dealYearMonth, Status status,
			int sourceTrades, int createdTrades, int skippedTrades,
			int updatedTrades, int conflictTrades, String failureReason
		) {
			this(districtCode, dealYearMonth, status, sourceTrades,
				createdTrades, skippedTrades, updatedTrades,
				conflictTrades, failureReason, 0);
		}
	}
}
