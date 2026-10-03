package com.geupjido.batch.trade.model;

import java.math.BigDecimal;
import java.time.LocalDate;

/**
 * 정규화된 아파트 매매 실거래가 한 건을 나타낸다.
 */
public record ApartmentTradeData(
	String districtCode,
	String legalDongName,
	String apartmentName,
	String lotNumber,
	String apartmentDong,
	Integer buildYear,
	LocalDate dealDate,
	long dealAmountTenThousandWon,
	BigDecimal exclusiveArea,
	Integer floor,
	String dealingType,
	boolean canceled,
	LocalDate cancellationDate
) {
}
