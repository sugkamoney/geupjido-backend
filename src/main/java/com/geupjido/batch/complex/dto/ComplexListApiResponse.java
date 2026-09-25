package com.geupjido.batch.complex.dto;

import java.util.List;

import com.fasterxml.jackson.annotation.JsonIgnoreProperties;

/**
 * 공동주택 단지 목록 API의 전체 응답 구조를 나타낸다.
 */
@JsonIgnoreProperties(ignoreUnknown = true)
public record ComplexListApiResponse(
	Result response
) {

	@JsonIgnoreProperties(ignoreUnknown = true)
	public record Result(
		Header header,
		Body body
	) {
	}

	@JsonIgnoreProperties(ignoreUnknown = true)
	public record Header(
		String resultCode,
		String resultMsg
	) {
	}

	@JsonIgnoreProperties(ignoreUnknown = true)
	public record Body(
		List<ComplexListApiItem> items,
		int numOfRows,
		int pageNo,
		int totalCount
	) {
		public Body {
			items = items == null ? List.of() : List.copyOf(items);
		}
	}
}
