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
		return assess(trade, candidateFinder.findCandidates(trade, scope));
	}

	ApartmentTradeCandidateAssessment assess(
		ApartmentTrade trade,
		List<Complex> candidates
	) {

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
			List<Complex> namedCandidates = candidates.stream()
				.filter(candidate -> isSameName(trade, candidate)
					|| ApprovedTradeNameAliases.matches(trade, candidate))
				.toList();
			if (namedCandidates.size() != 1
				|| !isConfirmedSaleType(namedCandidates.getFirst())) {
				return new ApartmentTradeCandidateAssessment(
					Status.MULTIPLE_CANDIDATES, candidateIds
				);
			}
			return assessSingle(trade, namedCandidates.getFirst());
		}

		return assessSingle(trade, candidates.getFirst());
	}

	private ApartmentTradeCandidateAssessment assessSingle(
		ApartmentTrade trade,
		Complex candidate
	) {
		List<String> candidateIds = List.of(candidate.getId());

		Status status = isSameName(trade, candidate)
			? Status.NAME_EXACT
			: ApprovedTradeNameAliases.matches(trade, candidate)
				? Status.NAME_APPROVED_ALIAS : Status.NAME_REVIEW;

		return new ApartmentTradeCandidateAssessment(
			status, candidateIds
		);
	}

	private static boolean isSameName(
		ApartmentTrade trade, Complex candidate
	) {
		return normalizeName(trade.getRawApartmentName()).equals(
			normalizeName(candidate.getName())
		);
	}

	private static boolean isConfirmedSaleType(Complex candidate) {
		String saleType = candidate.getSaleType();
		return saleType != null
			&& (saleType.contains("분양") || saleType.contains("혼합"))
			&& !candidate.isRentalOnly();
	}

	private static String normalizeName(String value) {
		String withoutWhitespace = value.replaceAll("\\s+", "");
		return withoutWhitespace.endsWith("아파트")
			? withoutWhitespace.substring(0,
				withoutWhitespace.length() - "아파트".length())
			: withoutWhitespace;
	}
}
