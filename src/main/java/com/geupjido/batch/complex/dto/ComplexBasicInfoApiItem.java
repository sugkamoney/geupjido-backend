package com.geupjido.batch.complex.dto;

import java.math.BigDecimal;

import com.fasterxml.jackson.annotation.JsonIgnoreProperties;

/**
 * 공동주택 단지 기본정보 API가 반환한 개별 단지 정보를 나타낸다.
 */
@JsonIgnoreProperties(ignoreUnknown = true)
public record ComplexBasicInfoApiItem(
	String kaptCode,
	String kaptName,
	String kaptAddr,
	String doroJuso,
	String kaptDongCnt,
	BigDecimal kaptdaCnt,
	String kaptUsedate,
	String bjdCode
) {
}
