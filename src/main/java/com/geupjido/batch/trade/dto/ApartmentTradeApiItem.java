package com.geupjido.batch.trade.dto;

import com.fasterxml.jackson.annotation.JsonIgnoreProperties;

/**
 * 아파트 매매 실거래가 API에서 조회한 거래 한 건을 나타낸다.
 */
@JsonIgnoreProperties(ignoreUnknown = true)
public record ApartmentTradeApiItem(
	String aptDong,
	String aptNm,
	String buildYear,
	String buyerGbn,
	String cdealDay,
	String cdealType,
	String dealAmount,
	String dealDay,
	String dealMonth,
	String dealYear,
	String dealingGbn,
	String estateAgentSggNm,
	String excluUseAr,
	String floor,
	String jibun,
	String landLeaseholdGbn,
	String rgstDate,
	String sggCd,
	String slerGbn,
	String umdNm
) {
}
