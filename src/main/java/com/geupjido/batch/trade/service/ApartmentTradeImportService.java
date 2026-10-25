package com.geupjido.batch.trade.service;

import java.util.List;

import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.stereotype.Service;

import com.geupjido.batch.trade.model.ApartmentTradeData;
import com.geupjido.batch.trade.model.ApartmentTradeImportResult;

/**
 * 아파트 매매 실거래가의 월별 수집과 저장을 연결한다.
 */
@Service
@ConditionalOnProperty(
	prefix = "app.external.apartment-trade",
	name = "enabled",
	havingValue = "true"
)
public class ApartmentTradeImportService {

	private final ApartmentTradeCollectionService collectionService;
	private final ApartmentTradePersistenceService persistenceService;

	public ApartmentTradeImportService(
		ApartmentTradeCollectionService collectionService,
		ApartmentTradePersistenceService persistenceService
	) {
		this.collectionService = collectionService;
		this.persistenceService = persistenceService;
	}

	public ApartmentTradeImportResult importMonth(
		String districtCode,
		String dealYearMonth
	) {
		List<ApartmentTradeData> trades = collectionService.collect(
			districtCode,
			dealYearMonth
		);

		return persistenceService.persist(trades);
	}
}
