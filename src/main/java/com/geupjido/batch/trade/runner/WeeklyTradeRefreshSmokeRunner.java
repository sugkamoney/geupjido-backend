package com.geupjido.batch.trade.runner;

import java.time.YearMonth;
import java.time.format.DateTimeFormatter;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.boot.ApplicationArguments;
import org.springframework.boot.ApplicationRunner;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.stereotype.Component;

import com.geupjido.batch.trade.config.WeeklyTradeRefreshProperties;
import com.geupjido.batch.trade.model.WeeklyTradeRefreshResult;
import com.geupjido.batch.trade.service.WeeklyTradeRefreshService;

/** 명시한 시군구·계약월 한 쌍으로 주간 갱신 흐름을 시험한다. */
@Component
@ConditionalOnProperty(
	prefix = "app.weekly-trade-refresh",
	name = "smoke-run-on-startup",
	havingValue = "true"
)
public class WeeklyTradeRefreshSmokeRunner implements ApplicationRunner {

	private static final Logger log =
		LoggerFactory.getLogger(WeeklyTradeRefreshSmokeRunner.class);
	private static final DateTimeFormatter MONTH_FORMAT =
		DateTimeFormatter.ofPattern("yyyyMM");

	private final WeeklyTradeRefreshProperties properties;
	private final WeeklyTradeRefreshService refreshService;

	public WeeklyTradeRefreshSmokeRunner(
		WeeklyTradeRefreshProperties properties,
		WeeklyTradeRefreshService refreshService
	) {
		this.properties = properties;
		this.refreshService = refreshService;
	}

	@Override
	public void run(ApplicationArguments args) {
		if (properties.runOnStartup() || properties.scheduleEnabled()) {
			throw new IllegalStateException(
				"시험 실행은 전체 수동 실행·주간 예약과 함께 켤 수 없습니다."
			);
		}
		String districtCode = properties.smokeDistrictCode();
		String dealYearMonth = properties.smokeDealYearMonth();
		if (districtCode == null || !districtCode.matches("\\d{5}")) {
			throw new IllegalStateException("시험 실행 시군구 코드는 숫자 5자리여야 합니다.");
		}
		if (dealYearMonth == null
			|| !dealYearMonth.matches("\\d{4}(0[1-9]|1[0-2])")) {
			throw new IllegalStateException(
				"시험 실행 계약월은 yyyyMM 형식이어야 합니다."
			);
		}
		YearMonth month = YearMonth.parse(dealYearMonth, MONTH_FORMAT);
		log.info("실거래가 주간 갱신 시험 실행 시작. 시군구={}, 계약월={}",
			districtCode, dealYearMonth);
		WeeklyTradeRefreshResult result = refreshService.refreshOne(
			properties.mappingPath(), properties.referencePath(),
			districtCode, month
		);
		log.info("실거래가 주간 갱신 시험 실행 결과. 시군구={}, 계약월={}, "
			+ "상태={}, 신규 연결={}, 이번 조합 법정동 미확인={}, "
			+ "전체 법정동 미확인={}, 이번 조합 권역 불일치={}, "
			+ "전체 권역 불일치={}, "
			+ "가격 있는 단지={}",
			districtCode, dealYearMonth, result.status(),
			result.link().linked(), result.link().currentUnknown(),
			result.link().unknown(), result.link().currentZoneMismatch(),
			result.link().zoneMismatch(), result.price().pricedComplexCount());
		if (result.status() == WeeklyTradeRefreshResult.Status.NEEDS_REVIEW
			|| result.status() == WeeklyTradeRefreshResult.Status.FAILED) {
			throw new IllegalStateException(
				"실거래가 주간 갱신 시험 실행에 실패 또는 연결 검토할 결과가 있습니다."
			);
		}
	}
}
