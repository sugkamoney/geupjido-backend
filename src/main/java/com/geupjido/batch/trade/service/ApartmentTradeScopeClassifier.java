package com.geupjido.batch.trade.service;

import java.util.HashMap;
import java.util.HashSet;
import java.util.Map;
import java.util.Set;

import com.geupjido.batch.location.exception.LocationMappingException;
import com.geupjido.batch.location.model.LegalDongCodeReference;
import com.geupjido.batch.location.model.LocationMapping;
import com.geupjido.batch.trade.model.ApartmentTradeScope;
import com.geupjido.batch.trade.model.ApartmentTradeScope.Status;

/**
 * 거래 법정동을 현재 권역 매핑과 대조한다. 단지 매칭은 수행하지 않는다.
 */
public class ApartmentTradeScopeClassifier {

	private final LegalDongCodeReference reference;
	private final Map<String, String> zoneByLegalDongCode;

	public ApartmentTradeScopeClassifier(
		LegalDongCodeReference reference,
		LocationMapping mapping
	) {
		if (reference == null || mapping == null) {
			throw new IllegalArgumentException(
				"법정동 참조 자료와 권역 매핑이 필요합니다."
			);
		}
		mapping.validate();

		Set<String> knownCodes = new HashSet<>(reference.codes().values());
		Map<String, String> zoneByCode = new HashMap<>();
		for (LocationMapping.ZoneDefinition zone : mapping.zones()) {
			for (String code : zone.dongCodes()) {
				if (!knownCodes.contains(code)) {
					throw new LocationMappingException(
						"법정동 참조 자료에 없는 권역 코드입니다: " + code
					);
				}
				zoneByCode.put(code, zone.id());
			}
		}

		this.reference = reference;
		this.zoneByLegalDongCode = Map.copyOf(zoneByCode);
	}

	public ApartmentTradeScope classify(
		String districtCode,
		String legalDongName
	) {
		String code = reference.findCode(districtCode, legalDongName)
			.orElse(null);
		if (code == null && legalDongName != null) {
			int separator = legalDongName.indexOf(' ');
			if (separator > 0) {
				String town = legalDongName.substring(0, separator);
				if (town.endsWith("읍") || town.endsWith("면")) {
					code = reference.findCode(districtCode, town)
						.orElse(null);
				}
			}
		}
		if (code == null) {
			return new ApartmentTradeScope(
				Status.UNKNOWN_LEGAL_DONG, null, null
			);
		}

		String zoneId = zoneByLegalDongCode.get(code);
		if (zoneId == null) {
			return new ApartmentTradeScope(Status.OUT_OF_SCOPE, code, null);
		}
		return new ApartmentTradeScope(Status.IN_SCOPE, code, zoneId);
	}
}
