package com.geupjido.batch.complex.service;

import java.time.LocalDate;
import java.util.List;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import com.geupjido.batch.complex.client.NaverGeocodingApiClient;
import com.geupjido.batch.complex.dto.NaverGeocodingResponse;
import com.geupjido.complex.domain.Complex;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoMoreInteractions;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class ComplexGeocodingLookupServiceTest {

	private static final String ROAD = "서울특별시 강남구 도곡로21길 15";
	private static final String JIBUN = "서울특별시 강남구 역삼동 795-41";

	@Mock
	private NaverGeocodingApiClient apiClient;

	@InjectMocks
	private ComplexGeocodingLookupService service;

	@Test
	void 도로명_조회가_성공하면_지번을_호출하지_않는다() {
		NaverGeocodingResponse.Address address = address();
		when(apiClient.geocode(ROAD)).thenReturn(response(address));

		assertThat(service.findAddress(complex(ROAD)))
			.contains(address);

		verify(apiClient).geocode(ROAD);
		verifyNoMoreInteractions(apiClient);
	}

	@Test
	void 도로명_결과가_없으면_지번으로_재조회한다() {
		NaverGeocodingResponse.Address address = address();
		when(apiClient.geocode(ROAD)).thenReturn(response());
		when(apiClient.geocode(JIBUN)).thenReturn(response(address));

		assertThat(service.findAddress(complex(ROAD)))
			.contains(address);

		verify(apiClient).geocode(ROAD);
		verify(apiClient).geocode(JIBUN);
	}

	@Test
	void 도로명_주소가_없으면_지번으로_조회한다() {
		NaverGeocodingResponse.Address address = address();
		when(apiClient.geocode(JIBUN)).thenReturn(response(address));

		assertThat(service.findAddress(complex(null)))
			.contains(address);

		verify(apiClient).geocode(JIBUN);
		verifyNoMoreInteractions(apiClient);
	}

	@Test
	void 복수_결과는_선택하거나_지번으로_재조회하지_않는다() {
		when(apiClient.geocode(ROAD))
			.thenReturn(response(address(), address()));

		assertThatThrownBy(() -> service.findAddress(complex(ROAD)))
			.isInstanceOf(IllegalStateException.class)
			.hasMessageContaining("여러 건");

		verify(apiClient).geocode(ROAD);
		verifyNoMoreInteractions(apiClient);
	}

	@Test
	void API_오류는_조회_결과_없음으로_취급하지_않는다() {
		IllegalStateException failure =
			new IllegalStateException("API 오류");
		when(apiClient.geocode(ROAD)).thenThrow(failure);

		assertThatThrownBy(() -> service.findAddress(complex(ROAD)))
			.isSameAs(failure);

		verify(apiClient).geocode(ROAD);
		verifyNoMoreInteractions(apiClient);
	}

	private static Complex complex(String roadAddress) {
		return new Complex(
			"A10019883",
			"역삼동월드메르디앙아파트",
			JIBUN,
			roadAddress,
			"1168010100",
			100,
			1,
			LocalDate.of(2001, 1, 1)
		);
	}

	private static NaverGeocodingResponse.Address address() {
		return new NaverGeocodingResponse.Address(
			ROAD,
			JIBUN,
			"127.0350000",
			"37.4950000"
		);
	}

	private static NaverGeocodingResponse response(
		NaverGeocodingResponse.Address... addresses
	) {
		return new NaverGeocodingResponse(
			"OK",
			new NaverGeocodingResponse.Meta(addresses.length),
			List.of(addresses),
			null
		);
	}
}
