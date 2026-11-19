package com.geupjido.report.runner;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.dao.CannotAcquireLockException;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;

import com.geupjido.report.service.WeeklyReportPublicationService;
import com.geupjido.report.service.WeeklyReportFailureService;

@Component
@ConditionalOnProperty(prefix = "app.weekly-report", name = "schedule-enabled", havingValue = "true")
public class WeeklyReportScheduler {
	private static final Logger log = LoggerFactory.getLogger(WeeklyReportScheduler.class);
	private final WeeklyReportPublicationService service;
	private final WeeklyReportFailureService failureService;

	public WeeklyReportScheduler(WeeklyReportPublicationService service,
		WeeklyReportFailureService failureService) {
		this.service = service;
		this.failureService = failureService;
	}

	@Scheduled(cron = "${app.weekly-report.schedule-cron:0 */10 * * * *}", zone = "Asia/Seoul")
	public void prepareAndPublish() {
		try {
			// 이미 완성된 초안은 21시 정각에 짧은 업데이트만으로 먼저 공개한다.
			if (service.publishLatestReady()) {
				log.info("주간 리포트 공개 완료");
			}
			if (prepareWithOneRetry()) {
				log.info("주간 리포트 초안 준비 완료");
			}
			if (service.publishLatestReady()) {
				log.info("주간 리포트 공개 완료");
			}
		} catch (RuntimeException exception) {
			log.error("주간 리포트 준비 또는 공개 실패. 다음 예약 실행에서 재시도합니다.", exception);
			try {
				failureService.recordLatestFailure();
			} catch (RuntimeException recordException) {
				log.error("주간 리포트 실패 상태 저장 실패", recordException);
			}
		}
	}

	private boolean prepareWithOneRetry() {
		try {
			return service.prepareLatestClosed();
		} catch (CannotAcquireLockException exception) {
			// 3시간 급지 발행과 겹친 REPEATABLE READ 직렬화 충돌은 새 트랜잭션에서 재시도한다.
			log.warn("주간 리포트 집계 잠금 충돌. 즉시 한 번 재시도합니다.");
			return service.prepareLatestClosed();
		}
	}
}
