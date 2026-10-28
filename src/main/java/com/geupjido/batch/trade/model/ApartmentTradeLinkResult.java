package com.geupjido.batch.trade.model;

public record ApartmentTradeLinkResult(
	int proposalCount,
	int pendingCount,
	int rejectedCount,
	int approvedCount,
	int linkedTradeCount,
	int alreadyLinkedTradeCount
) {
}
