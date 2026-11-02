package com.geupjido.batch.trade.service;

import java.nio.file.Path;
import java.time.YearMonth;
import java.time.ZoneId;
import java.util.List;
import java.util.Map;
import java.util.concurrent.atomic.AtomicReference;

import org.junit.jupiter.api.Test;

import com.geupjido.batch.trade.config.WeeklyTradeRefreshProperties;
import com.geupjido.batch.trade.model.ComplexRepresentativePriceResult;
import com.geupjido.batch.trade.model.WeeklyTradeCollectionResult;
import com.geupjido.batch.trade.model.WeeklyTradeCollectionResult.PairResult;
import com.geupjido.batch.trade.model.WeeklyTradeCollectionResult.Status;
import com.geupjido.batch.trade.model.WeeklyTradeRefreshResult;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

class WeeklyTradeRefreshJobTest {

	@Test
	void 한국_기준월로_수동과_예약_공통_작업을_실행한다() {
		WeeklyTradeRefreshService service = mock(WeeklyTradeRefreshService.class);
		WeeklyTradeRefreshProperties properties = properties();
		when(service.refresh(eq(properties.mappingPath()),
			eq(properties.referencePath()), any(YearMonth.class), eq(3)))
			.thenReturn(result(WeeklyTradeRefreshResult.Status.COMPLETE));

		new WeeklyTradeRefreshJob(properties, service).execute();

		verify(service).refresh(properties.mappingPath(),
			properties.referencePath(),
			YearMonth.now(ZoneId.of("Asia/Seoul")), 3);
	}

	@Test
	void 검토_필요_결과는_완료로_취급하지_않는다() {
		WeeklyTradeRefreshService service = mock(WeeklyTradeRefreshService.class);
		when(service.refresh(any(), any(), any(), eq(3)))
			.thenReturn(result(WeeklyTradeRefreshResult.Status.NEEDS_REVIEW));

		assertThatThrownBy(() -> new WeeklyTradeRefreshJob(
			properties(), service
		).execute()).isInstanceOf(IllegalStateException.class)
			.hasMessageContaining("검토할 결과");
	}

	@Test
	void 원본_충돌만_있으면_예외_없이_완료_상태를_반환한다() {
		WeeklyTradeRefreshService service = mock(WeeklyTradeRefreshService.class);
		when(service.refresh(any(), any(), any(), eq(3)))
			.thenReturn(result(WeeklyTradeRefreshResult.Status.COMPLETE_WITH_CONFLICTS));

		assertThat(new WeeklyTradeRefreshJob(properties(), service)
			.execute().status()).isEqualTo(
			WeeklyTradeRefreshResult.Status.COMPLETE_WITH_CONFLICTS);
	}

	@Test
	void 수집_실패는_예외로_알린다() {
		WeeklyTradeRefreshService service = mock(WeeklyTradeRefreshService.class);
		when(service.refresh(any(), any(), any(), eq(3)))
			.thenReturn(result(WeeklyTradeRefreshResult.Status.FAILED));

		assertThatThrownBy(() -> new WeeklyTradeRefreshJob(
			properties(), service
		).execute()).isInstanceOf(IllegalStateException.class);
	}

	@Test
	void 이미_실행_중이면_중복_작업을_거부한다() {
		WeeklyTradeRefreshService service = mock(WeeklyTradeRefreshService.class);
		AtomicReference<WeeklyTradeRefreshJob> job = new AtomicReference<>();
		when(service.refresh(any(), any(), any(), eq(3)))
			.thenAnswer(ignored -> {
				assertThatThrownBy(() -> job.get().execute())
					.isInstanceOf(IllegalStateException.class)
					.hasMessageContaining("이미 실행 중");
				return result(WeeklyTradeRefreshResult.Status.COMPLETE);
			});
		job.set(new WeeklyTradeRefreshJob(properties(), service));

		assertThat(job.get().execute().status())
			.isEqualTo(WeeklyTradeRefreshResult.Status.COMPLETE);
	}

	private static WeeklyTradeRefreshProperties properties() {
		return new WeeklyTradeRefreshProperties(false, false, false,
			null, null,
			Path.of("mapping.json"), Path.of("dong.tsv"), 3,
			Path.of("weekly.tsv"));
	}

	private static WeeklyTradeRefreshResult result(
		WeeklyTradeRefreshResult.Status status
	) {
		return new WeeklyTradeRefreshResult(
			new WeeklyTradeCollectionResult(List.of(
				new PairResult("11110", "202611", Status.SUCCESS,
					1, 1, 0, 0, 0, null)
			)),
			new ApartmentTradeExactLinkService.Result(
				0, 1, 1, 0, 0, 1, 0, Map.of()
			),
			new ComplexRepresentativePriceResult(1, 1, 1, 0),
			status
		);
	}
}
