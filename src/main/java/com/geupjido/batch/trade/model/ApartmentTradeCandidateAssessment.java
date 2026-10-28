package com.geupjido.batch.trade.model;

import java.util.List;

public record ApartmentTradeCandidateAssessment(
	Status status,
	List<String> candidateIds
) {
	public enum Status {
		NO_CANDIDATE,
		MULTIPLE_CANDIDATES,
		YEAR_UNVERIFIED,
		YEAR_MISMATCH,
		NAME_EXACT,
		NAME_REVIEW
	}

	public ApartmentTradeCandidateAssessment {
		candidateIds = List.copyOf(candidateIds);
	}
}
