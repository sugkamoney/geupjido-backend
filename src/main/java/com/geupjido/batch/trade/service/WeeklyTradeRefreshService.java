package com.geupjido.batch.trade.service;

import java.nio.file.Files;
import java.nio.file.Path;
import java.time.YearMonth;
import java.util.Set;
import java.util.stream.Collectors;

import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.stereotype.Service;

import com.geupjido.batch.trade.model.ComplexRepresentativePriceResult;
import com.geupjido.batch.trade.model.WeeklyTradeCollectionResult;
import com.geupjido.batch.trade.model.WeeklyTradeCollectionResult.Status;
import com.geupjido.batch.trade.model.WeeklyTradeRefreshResult;

/** 주간 수집에서 저장된 거래를 단지에 연결한 뒤 대표 시세를 갱신한다. */
@Service
@ConditionalOnProperty(
	prefix = "app.external.apartment-trade",
	name = "enabled",
	havingValue = "true"
)
public class WeeklyTradeRefreshService {

	private final WeeklyTradeCollectionService collectionService;
	private final ApartmentTradeExactLinkService linkService;
	private final ComplexRepresentativePriceService priceService;

	public WeeklyTradeRefreshService(
		WeeklyTradeCollectionService collectionService,
		ApartmentTradeExactLinkService linkService,
		ComplexRepresentativePriceService priceService
	) {
		this.collectionService = collectionService;
		this.linkService = linkService;
		this.priceService = priceService;
	}

	public WeeklyTradeRefreshResult refresh(
		Path mappingPath,
		Path referencePath,
		YearMonth currentMonth,
		int monthsToCollect
	) {
		requireFile(mappingPath, "권역 매핑 파일");
		requireFile(referencePath, "법정동 코드 참조 파일");
		WeeklyTradeCollectionResult collection = collectionService.collect(
			mappingPath, currentMonth, monthsToCollect
		);
		return finish(mappingPath, referencePath, collection);
	}

	public WeeklyTradeRefreshResult refreshOne(
		Path mappingPath,
		Path referencePath,
		String districtCode,
		YearMonth dealMonth
	) {
		requireFile(mappingPath, "권역 매핑 파일");
		requireFile(referencePath, "법정동 코드 참조 파일");
		WeeklyTradeCollectionResult collection = collectionService.collectOne(
			mappingPath, districtCode, dealMonth
		);
		return finish(mappingPath, referencePath, collection);
	}

	private WeeklyTradeRefreshResult finish(
		Path mappingPath, Path referencePath,
		WeeklyTradeCollectionResult collection
	) {
		Set<ApartmentTradeExactLinkService.TradeMonth> currentPairs =
			collection.pairs().stream()
				.filter(pair -> pair.status() != Status.FAILED)
				.map(pair -> new ApartmentTradeExactLinkService.TradeMonth(
					pair.districtCode(), pair.dealYearMonth()))
				.collect(Collectors.toUnmodifiableSet());
		ApartmentTradeExactLinkService.Result link =
			linkService.linkExact(referencePath, mappingPath, currentPairs);
		ComplexRepresentativePriceResult price = priceService.recalculateAll();
		WeeklyTradeRefreshResult.Status status;
		if (collection.count(Status.FAILED) > 0) {
			status = WeeklyTradeRefreshResult.Status.FAILED;
		} else if (link.currentUnknown() > 0
			|| link.currentZoneMismatch() > 0) {
			status = WeeklyTradeRefreshResult.Status.NEEDS_REVIEW;
		} else if (collection.count(Status.PARTIAL) > 0) {
			status = WeeklyTradeRefreshResult.Status.COMPLETE_WITH_CONFLICTS;
		} else {
			status = WeeklyTradeRefreshResult.Status.COMPLETE;
		}
		return new WeeklyTradeRefreshResult(collection, link, price, status);
	}

	private static void requireFile(Path path, String name) {
		if (path == null || !Files.isRegularFile(path)) {
			throw new IllegalStateException(name + "이(가) 없습니다: " + path);
		}
	}
}
