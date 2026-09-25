package com.geupjido.batch.complex.dto;

import com.fasterxml.jackson.annotation.JsonIgnoreProperties;

/**
 * 공동주택 단지 목록 API가 반환한 개별 단지 정보를 나타낸다.
 */
@JsonIgnoreProperties(ignoreUnknown = true)
public record ComplexListApiItem(
	String kaptCode,
	String kaptName,
	String bjdCode
) {
}
