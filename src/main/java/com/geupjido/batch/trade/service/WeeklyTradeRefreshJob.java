package com.geupjido.batch.trade.service;

import java.time.YearMonth;
import java.time.ZoneId;
import java.util.concurrent.atomic.AtomicBoolean;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.stereotype.Component;

import com.geupjido.batch.trade.config.WeeklyTradeRefreshProperties;
import com.geupjido.batch.trade.model.WeeklyTradeCollectionResult.Status;
import com.geupjido.batch.trade.model.WeeklyTradeRefreshResult;

/** 수동·예약 실행이 공유하는 진입점과 단일 프로세스 중복 실행 방지. */
@Component
@ConditionalOnProperty(
	prefix = "app.external.apartment-trade",
	name = "enabled",
	havingValue = "true"
)
public class WeeklyTradeRefreshJob {

	private static final Logger log =
		LoggerFactory.getLogger(WeeklyTradeRefreshJob.class);
	private static final ZoneId KOREA_TIME = ZoneId.of("Asia/Seoul");

	private final WeeklyTradeRefreshProperties properties;
	private final WeeklyTradeRefreshService refreshService;
	private final AtomicBoolean running = new AtomicBoolean();

	public WeeklyTradeRefreshJob(
		WeeklyTradeRefreshProperties properties,
		WeeklyTradeRefreshService refreshService
	) {
		this.properties = properties;
		this.refreshService = refreshService;
	}

	public WeeklyTradeRefreshResult execute() {
		if (!running.compareAndSet(false, true)) {
			throw new IllegalStateException("실거래가 주간 갱신이 이미 실행 중입니다.");
		}
		try {
			YearMonth currentMonth = YearMonth.now(KOREA_TIME);
			log.info("실거래가 주간 갱신 시작. 기준월={}, 조회 개월 수={}",
				currentMonth, properties.monthsToCollect());
			WeeklyTradeRefreshResult result = refreshService.refresh(
				properties.mappingPath(), properties.referencePath(),
				currentMonth, properties.monthsToCollect()
			);
			if (result.status() == WeeklyTradeRefreshResult.Status.FAILED
				|| result.status() == WeeklyTradeRefreshResult.Status.NEEDS_REVIEW) {
				log.warn("실거래가 주간 갱신 미완료. 상태={}, 대상={}, 성공={}, "
					+ "충돌 조합={}, 원본 충돌={}, 신규충돌={}, 실패={}, "
					+ "신규 연결={}, 가격 있는 단지={}",
					result.status(),
					result.collection().pairs().size(),
					result.collection().count(Status.SUCCESS),
					result.collection().count(Status.PARTIAL),
					result.collection().conflictTrades(),
					result.collection().newConflictTrades(),
					result.collection().count(Status.FAILED),
					result.link().linked(), result.price().pricedComplexCount());
				throw new IllegalStateException(
					"실거래가 주간 갱신에 실패 또는 연결 검토할 결과가 있습니다."
				);
			}
			log.info("실거래가 주간 갱신 완료. 상태={}, 대상={}, 성공={}, "
				+ "충돌 조합={}, 원본 충돌={}, 신규충돌={}, 신규 연결={}, "
				+ "가격 있는 단지={}", result.status(),
				result.collection().pairs().size(),
				result.collection().count(Status.SUCCESS),
				result.collection().count(Status.PARTIAL),
				result.collection().conflictTrades(),
				result.collection().newConflictTrades(),
				result.link().linked(), result.price().pricedComplexCount());
			return result;
		} finally {
			running.set(false);
		}
	}
}
