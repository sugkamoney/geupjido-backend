package com.geupjido.batch.trade.service;

import java.util.List;

import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.stereotype.Service;

import com.geupjido.batch.trade.model.ApartmentTradeData;
import com.geupjido.batch.trade.model.ApartmentTradeImportResult;
import com.geupjido.batch.trade.model.ApartmentTradePartialImportResult;

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
	private final ApartmentTradeBackfillPersistenceService backfillPersistenceService;

	public ApartmentTradeImportService(
		ApartmentTradeCollectionService collectionService,
		ApartmentTradePersistenceService persistenceService,
		ApartmentTradeBackfillPersistenceService backfillPersistenceService
	) {
		this.collectionService = collectionService;
		this.persistenceService = persistenceService;
		this.backfillPersistenceService = backfillPersistenceService;
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

	public ApartmentTradePartialImportResult importBackfillMonth(
		String districtCode, String dealYearMonth
	) {
		List<ApartmentTradeData> trades = collectionService.collect(
			districtCode, dealYearMonth
		);
		return backfillPersistenceService.persist(
			districtCode, dealYearMonth, trades
		);
	}
}
