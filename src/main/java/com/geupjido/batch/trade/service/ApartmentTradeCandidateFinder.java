package com.geupjido.batch.trade.service;

import java.util.List;
import java.util.regex.Pattern;

import org.springframework.stereotype.Component;

import com.geupjido.batch.trade.model.ApartmentTradeScope;
import com.geupjido.complex.domain.Complex;
import com.geupjido.complex.repository.ComplexRepository;
import com.geupjido.trade.domain.ApartmentTrade;

@Component
public class ApartmentTradeCandidateFinder {

	private final ComplexRepository complexRepository;

	public ApartmentTradeCandidateFinder(
		ComplexRepository complexRepository
	) {
		this.complexRepository = complexRepository;
	}

	public List<Complex> findCandidates(
		ApartmentTrade trade,
		ApartmentTradeScope scope
	) {
		if (scope.status() != ApartmentTradeScope.Status.IN_SCOPE) {
			throw new IllegalArgumentException(
				"서비스 대상 거래만 단지 후보를 조회할 수 있습니다."
			);
		}

		String lotNumber = trade.getLotNumber();
		if (lotNumber == null || lotNumber.isBlank()) {
			return List.of();
		}

		Pattern addressPattern = Pattern.compile(
			"(?:^|\\s)"
				+ Pattern.quote(trade.getLegalDongName())
				+ "\\s+"
				+ Pattern.quote(lotNumber)
				+ "(?=\\s|$)"
		);

		return complexRepository
			.findByLegalDongCode(scope.legalDongCode())
			.stream()
			.filter(complex ->
				addressPattern.matcher(
					complex.getAddressJibun()
				).find()
			)
			.toList();
	}
}
