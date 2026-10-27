package com.geupjido.batch.complex.dto;

import java.util.List;

import com.fasterxml.jackson.annotation.JsonIgnoreProperties;

@JsonIgnoreProperties(ignoreUnknown = true)
public record NaverGeocodingResponse(
	String status,
	Meta meta,
	List<Address> addresses,
	String errorMessage
) {
	@JsonIgnoreProperties(ignoreUnknown = true)
	public record Meta(int totalCount) {
	}

	@JsonIgnoreProperties(ignoreUnknown = true)
	public record Address(
		String roadAddress,
		String jibunAddress,
		String x,
		String y
	) {
	}
}
