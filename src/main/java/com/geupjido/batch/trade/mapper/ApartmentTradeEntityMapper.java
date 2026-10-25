package com.geupjido.batch.trade.mapper;

import com.geupjido.batch.trade.exception.ApartmentTradeMappingException;
import com.geupjido.batch.trade.model.ApartmentTradeData;
import com.geupjido.trade.domain.ApartmentTrade;
import org.springframework.stereotype.Component;

/**
 * 수집한 아파트 매매 실거래가를 저장용 엔티티로 변환한다.
 */
@Component
public class ApartmentTradeEntityMapper {

	public ApartmentTrade map(ApartmentTradeData data) {
		if (data == null) {
			throw new ApartmentTradeMappingException(
				"저장할 아파트 매매 실거래가가 없습니다."
			);
		}

		return new ApartmentTrade(
			data.dealDate(),
			data.dealAmountTenThousandWon(),
			data.exclusiveArea(),
			toShort(data.floor(), "층"),
			toShort(data.buildYear(), "건축연도"),
			data.districtCode(),
			data.legalDongName(),
			data.apartmentName(),
			data.lotNumber(),
			data.apartmentDong(),
			data.dealingType(),
			data.canceled(),
			data.cancellationDate()
		);
	}

	private Short toShort(Integer value, String fieldName) {
		if (value == null) {
			return null;
		}

		if (value < Short.MIN_VALUE || value > Short.MAX_VALUE) {
			throw new ApartmentTradeMappingException(
				fieldName + "이(가) SMALLINT 범위를 벗어났습니다: " + value
			);
		}

		return value.shortValue();
	}
}
