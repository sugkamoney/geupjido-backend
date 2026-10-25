package com.geupjido.batch.trade.runner;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.boot.ApplicationArguments;
import org.springframework.boot.ApplicationRunner;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.stereotype.Component;

import com.geupjido.batch.trade.config.ApartmentTradeImportProperties;
import com.geupjido.batch.trade.model.ApartmentTradeImportResult;
import com.geupjido.batch.trade.service.ApartmentTradeImportService;

@Component
@ConditionalOnProperty(
	prefix = "app.apartment-trade-import",
	name = "enabled",
	havingValue = "true"
)
public class ApartmentTradeImportRunner implements ApplicationRunner {

	private static final Logger log =
		LoggerFactory.getLogger(ApartmentTradeImportRunner.class);

	private final ApartmentTradeImportProperties properties;
	private final ApartmentTradeImportService importService;

	public ApartmentTradeImportRunner(
		ApartmentTradeImportProperties properties,
		ApartmentTradeImportService importService
	) {
		this.properties = properties;
		this.importService = importService;
	}

	@Override
	public void run(ApplicationArguments args) {
		String districtCode = properties.districtCode();
		String dealYearMonth = properties.dealYearMonth();

		if (districtCode == null || !districtCode.matches("\\d{5}")) {
			throw new IllegalStateException("시군구 코드는 숫자 5자리여야 합니다.");
		}
		if (dealYearMonth == null
			|| !dealYearMonth.matches("\\d{4}(0[1-9]|1[0-2])")) {
			throw new IllegalStateException("계약월은 yyyyMM 형식이어야 합니다.");
		}

		log.info("실거래가 수동 적재 시작. 시군구={}, 계약월={}",
			districtCode, dealYearMonth);

		ApartmentTradeImportResult result =
			importService.importMonth(districtCode, dealYearMonth);

		log.info(
			"실거래가 수동 적재 완료. 전체={}, 신규={}, 건너뛰기={}, 갱신={}",
			result.totalCount(),
			result.createdCount(),
			result.skippedCount(),
			result.updatedCount()
		);
	}
}
