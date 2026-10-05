package com.geupjido.batch.trade.service;

import java.util.List;

import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.stereotype.Service;

import com.geupjido.batch.trade.client.ApartmentTradeApiClient;
import com.geupjido.batch.trade.dto.ApartmentTradeApiItem;
import com.geupjido.batch.trade.mapper.ApartmentTradeMapper;
import com.geupjido.batch.trade.model.ApartmentTradeData;

/**
 * 시군구 코드와 계약월을 기준으로 아파트 매매 실거래가를 수집한다.
 */
@Service
@ConditionalOnProperty(
	prefix = "app.external.apartment-trade",
	name = "enabled",
	havingValue = "true"
)
public class ApartmentTradeCollectionService {

	private final ApartmentTradeApiClient apiClient;
	private final ApartmentTradeMapper mapper;

	public ApartmentTradeCollectionService(
		ApartmentTradeApiClient apiClient,
		ApartmentTradeMapper mapper
	) {
		this.apiClient = apiClient;
		this.mapper = mapper;
	}

	public List<ApartmentTradeData> collect(
		String districtCode,
		String dealYearMonth
	) {
		List<ApartmentTradeApiItem> items =
			apiClient.fetchAll(
				districtCode,
				dealYearMonth
			);

		return items.stream().map(mapper::map).toList();
	}
}
