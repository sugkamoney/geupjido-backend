package com.geupjido.batch.complex.service;

import java.util.Objects;
import java.util.Optional;

import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.stereotype.Service;

import com.geupjido.batch.complex.client.NaverGeocodingApiClient;
import com.geupjido.batch.complex.dto.NaverGeocodingResponse;
import com.geupjido.complex.domain.Complex;

@Service
@ConditionalOnProperty(
	prefix = "app.external.geocoding",
	name = "enabled",
	havingValue = "true"
)
public class ComplexGeocodingLookupService {

	private final NaverGeocodingApiClient apiClient;

	public ComplexGeocodingLookupService(
		NaverGeocodingApiClient apiClient
	) {
		this.apiClient = apiClient;
	}

	public Optional<NaverGeocodingResponse.Address> findAddress(
		Complex complex
	) {
		Objects.requireNonNull(complex, "단지가 필요합니다.");

		String roadAddress = complex.getAddressRoad();
		if (roadAddress != null && !roadAddress.isBlank()) {
			Optional<NaverGeocodingResponse.Address> result =
				lookup(roadAddress);
			if (result.isPresent()) {
				return result;
			}
		}

		return lookup(complex.getAddressJibun());
	}

	private Optional<NaverGeocodingResponse.Address> lookup(
		String address
	) {
		NaverGeocodingResponse response = apiClient.geocode(address);
		int count = response.meta().totalCount();

		if (count == 0 && response.addresses().isEmpty()) {
			return Optional.empty();
		}
		if (count > 1) {
			throw new IllegalStateException(
				"지오코딩 결과가 여러 건이어서 자동 선택할 수 없습니다."
			);
		}
		if (count != 1 || response.addresses().size() != 1
			|| response.addresses().getFirst() == null) {
			throw new IllegalStateException(
				"지오코딩 결과 건수와 주소 목록이 일치하지 않습니다."
			);
		}

		return Optional.of(response.addresses().getFirst());
	}
}
