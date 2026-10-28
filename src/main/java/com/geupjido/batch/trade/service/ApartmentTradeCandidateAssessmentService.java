package com.geupjido.batch.trade.service;

import java.util.List;

import org.springframework.stereotype.Service;

import com.geupjido.batch.trade.model.ApartmentTradeCandidateAssessment;
import com.geupjido.batch.trade.model.ApartmentTradeCandidateAssessment.Status;
import com.geupjido.batch.trade.model.ApartmentTradeScope;
import com.geupjido.complex.domain.Complex;
import com.geupjido.trade.domain.ApartmentTrade;

@Service
public class ApartmentTradeCandidateAssessmentService {

	private final ApartmentTradeCandidateFinder candidateFinder;

	public ApartmentTradeCandidateAssessmentService(
		ApartmentTradeCandidateFinder candidateFinder
	) {
		this.candidateFinder = candidateFinder;
	}

	public ApartmentTradeCandidateAssessment assess(
		ApartmentTrade trade,
		ApartmentTradeScope scope
	) {
		List<Complex> candidates =
			candidateFinder.findCandidates(trade, scope);

		List<String> candidateIds = candidates.stream()
			.map(Complex::getId)
			.sorted()
			.toList();

		if (candidates.isEmpty()) {
			return new ApartmentTradeCandidateAssessment(
				Status.NO_CANDIDATE, candidateIds
			);
		}

		if (candidates.size() > 1) {
			return new ApartmentTradeCandidateAssessment(
				Status.MULTIPLE_CANDIDATES, candidateIds
			);
		}

		Complex candidate = candidates.getFirst();

		if (
			trade.getBuildYear() == null
				|| candidate.getApprovalDate() == null
		) {
			return new ApartmentTradeCandidateAssessment(
				Status.YEAR_UNVERIFIED, candidateIds
			);
		}

		if (
			trade.getBuildYear().intValue()
				!= candidate.getApprovalDate().getYear()
		) {
			return new ApartmentTradeCandidateAssessment(
				Status.YEAR_MISMATCH, candidateIds
			);
		}

		String tradeName = removeWhitespace(
			trade.getRawApartmentName()
		);
		String candidateName = removeWhitespace(
			candidate.getName()
		);

		Status status = tradeName.equals(candidateName)
			? Status.NAME_EXACT
			: Status.NAME_REVIEW;

		return new ApartmentTradeCandidateAssessment(
			status, candidateIds
		);
	}

	private static String removeWhitespace(String value) {
		return value.replaceAll("\\s+", "");
	}
}
