package com.geupjido.batch.location.model;

import java.util.Map;
import java.util.Optional;

/**
 * 시군구 코드와 법정동 이름으로 10자리 법정동 코드를 찾는 참조 자료다.
 */
public record LegalDongCodeReference(Map<Key, String> codes) {

	public LegalDongCodeReference {
		codes = Map.copyOf(codes);
	}

	public Optional<String> findCode(
		String districtCode,
		String legalDongName
	) {
		if (districtCode == null || legalDongName == null) {
			return Optional.empty();
		}
		return Optional.ofNullable(
			codes.get(new Key(districtCode, legalDongName))
		);
	}

	public record Key(String districtCode, String legalDongName) {
	}
}
