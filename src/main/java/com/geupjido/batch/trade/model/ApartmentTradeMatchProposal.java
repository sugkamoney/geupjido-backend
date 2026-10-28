package com.geupjido.batch.trade.model;

import java.time.YearMonth;

/**
 * 원본 거래명·주소와 단지 마스터 사이의 사람이 검토할 연결 제안이다.
 */
public record ApartmentTradeMatchProposal(
	Key key,
	String candidateId,
	String candidateName,
	ApartmentTradeCandidateAssessment.Status assessmentStatus,
	Decision decision
) {
	public enum Decision {
		PENDING,
		APPROVED,
		REJECTED
	}

	public record Key(
		YearMonth dealYearMonth,
		String legalDongCode,
		String legalDongName,
		String lotNumber,
		String rawApartmentName,
		short buildYear
	) {
	}
}
