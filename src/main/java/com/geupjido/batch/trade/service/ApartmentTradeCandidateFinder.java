package com.geupjido.batch.trade.service;

import java.util.List;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

import org.springframework.stereotype.Component;

import com.geupjido.batch.trade.model.ApartmentTradeScope;
import com.geupjido.complex.domain.Complex;
import com.geupjido.complex.repository.ComplexRepository;
import com.geupjido.trade.domain.ApartmentTrade;

@Component
public class ApartmentTradeCandidateFinder {
	private static final Pattern NUMERIC_LOT = Pattern.compile(
		"[0-9]{1,8}(?:-[0-9]{0,8})?"
	);

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
		if (trade.getLotNumber() == null
			|| trade.getLotNumber().isBlank()) {
			return List.of();
		}
		return findCandidates(trade, scope,
			complexRepository.findByLegalDongCode(scope.legalDongCode()));
	}

	List<Complex> findCandidates(
		ApartmentTrade trade,
		ApartmentTradeScope scope,
		List<Complex> complexes
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
		Pattern numericAddressPattern = Pattern.compile(
			"(?:^|\\s)"
				+ Pattern.quote(trade.getLegalDongName())
				+ "\\s+([0-9]{1,8}(?:-[0-9]{0,8})?)(?=\\s|$)"
		);

		return complexes.stream()
			.filter(complex -> !complex.isRentalOnly())
			.filter(complex -> matchesLot(complex, lotNumber,
				addressPattern, numericAddressPattern))
			.toList();
	}

	private static boolean matchesLot(
		Complex complex, String tradeLot,
		Pattern exactPattern, Pattern numericPattern
	) {
		String address = complex.getAddressJibun();
		if (exactPattern.matcher(address).find()) {
			return true;
		}
		String saleType = complex.getSaleType();
		if (saleType == null
			|| !(saleType.contains("분양") || saleType.contains("혼합"))
			|| !NUMERIC_LOT.matcher(tradeLot).matches()) {
			return false;
		}
		String normalizedTradeLot = normalizeNumericLot(tradeLot);
		Matcher matcher = numericPattern.matcher(address);
		while (matcher.find()) {
			if (normalizedTradeLot.equals(
				normalizeNumericLot(matcher.group(1)))) {
				return true;
			}
		}
		return false;
	}

	private static String normalizeNumericLot(String value) {
		String[] parts = value.split("-", -1);
		int main = Integer.parseInt(parts[0]);
		int sub = parts.length == 1 || parts[1].isEmpty()
			? 0 : Integer.parseInt(parts[1]);
		return main + "-" + sub;
	}
}
