package com.geupjido.batch.trade.runner;

import java.time.YearMonth;
import java.time.format.DateTimeParseException;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.boot.ApplicationArguments;
import org.springframework.boot.ApplicationRunner;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.stereotype.Component;

import com.geupjido.batch.trade.config.ApartmentTradeBackfillProperties;
import com.geupjido.batch.trade.model.ApartmentTradeBackfillResult;
import com.geupjido.batch.trade.service.ApartmentTradeBackfillService;

/** 서비스 대상 시군구·계약월의 거래 원본을 공공 API에서 수집한다. */
@Component
@ConditionalOnProperty(
	prefix = "app.apartment-trade-backfill",
	name = "enabled",
	havingValue = "true"
)
public class ApartmentTradeBackfillRunner implements ApplicationRunner {

	private static final Logger log =
		LoggerFactory.getLogger(ApartmentTradeBackfillRunner.class);

	private final ApartmentTradeBackfillProperties properties;
	private final ApartmentTradeBackfillService service;

	public ApartmentTradeBackfillRunner(
		ApartmentTradeBackfillProperties properties,
		ApartmentTradeBackfillService service
	) {
		this.properties = properties;
		this.service = service;
	}

	@Override
	public void run(ApplicationArguments args) throws Exception {
		YearMonth from = parseMonth(properties.fromMonth());
		YearMonth through = parseMonth(properties.throughMonth());
		ApartmentTradeBackfillResult result = service.importRange(
			properties.mappingPath(), from, through,
			properties.maxPairs(), properties.manifestPath()
		);
		log.info(
			"실거래가 소급 적재 실행 완료. 계획 조합={}, 이전 완료={}, "
				+ "이번 처리={}, 실패={}, 원본={}, 신규={}, 건너뛰기={}, 갱신={}, 보류={}",
			result.plannedPairs(), result.alreadyCompletedPairs(),
			result.processedPairs(), result.failedPairs(), result.sourceTrades(),
			result.createdTrades(), result.skippedTrades(),
			result.updatedTrades(), result.conflictTrades()
		);
	}

	private static YearMonth parseMonth(String value) {
		try {
			if (value == null || !value.matches("\\d{4}(0[1-9]|1[0-2])")) {
				throw new IllegalArgumentException();
			}
			return YearMonth.parse(value,
				java.time.format.DateTimeFormatter.ofPattern("yyyyMM"));
		} catch (IllegalArgumentException | DateTimeParseException exception) {
			throw new IllegalStateException(
				"실거래가 소급 적재 계약월은 yyyyMM 형식이어야 합니다: "
					+ value, exception
			);
		}
	}
}
