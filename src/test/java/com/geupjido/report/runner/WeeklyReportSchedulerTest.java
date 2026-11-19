package com.geupjido.report.runner;

import org.junit.jupiter.api.Test;
import org.springframework.dao.CannotAcquireLockException;

import com.geupjido.report.service.WeeklyReportFailureService;
import com.geupjido.report.service.WeeklyReportPublicationService;

import static org.mockito.Mockito.doThrow;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;

class WeeklyReportSchedulerTest {
	private final WeeklyReportPublicationService service = mock(WeeklyReportPublicationService.class);
	private final WeeklyReportFailureService failures = mock(WeeklyReportFailureService.class);
	private final WeeklyReportScheduler scheduler = new WeeklyReportScheduler(service, failures);

	@Test
	void 급지_발행과_잠금이_겹치면_새_시도로_복구한다() {
		when(service.prepareLatestClosed())
			.thenThrow(new CannotAcquireLockException("직렬화 충돌"))
			.thenReturn(false);

		scheduler.prepareAndPublish();

		verify(service, org.mockito.Mockito.times(2)).prepareLatestClosed();
		verify(service, org.mockito.Mockito.times(2)).publishLatestReady();
		verifyNoInteractions(failures);
	}

	@Test
	void 두_시도_모두_실패하면_실패_상태를_기록한다() {
		doThrow(new CannotAcquireLockException("직렬화 충돌"))
			.when(service).prepareLatestClosed();

		scheduler.prepareAndPublish();

		verify(service, org.mockito.Mockito.times(2)).prepareLatestClosed();
		verify(failures).recordLatestFailure();
	}
}
