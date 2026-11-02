package com.geupjido.batch.trade.runner;

import java.nio.file.Path;
import java.time.YearMonth;
import java.util.List;
import java.util.Map;

import org.junit.jupiter.api.Test;

import com.geupjido.batch.trade.config.WeeklyTradeRefreshProperties;
import com.geupjido.batch.trade.model.ComplexRepresentativePriceResult;
import com.geupjido.batch.trade.model.WeeklyTradeCollectionResult;
import com.geupjido.batch.trade.model.WeeklyTradeCollectionResult.PairResult;
import com.geupjido.batch.trade.model.WeeklyTradeCollectionResult.Status;
import com.geupjido.batch.trade.model.WeeklyTradeRefreshResult;
import com.geupjido.batch.trade.service.ApartmentTradeExactLinkService;
import com.geupjido.batch.trade.service.WeeklyTradeRefreshService;

import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;

class WeeklyTradeRefreshSmokeRunnerTest {

	@Test
	void 한_시군구와_계약월로_시험_실행한다() {
		WeeklyTradeRefreshProperties properties = properties(
			false, false, "11170", "202611"
		);
		WeeklyTradeRefreshService service = mock(WeeklyTradeRefreshService.class);
		when(service.refreshOne(properties.mappingPath(),
			properties.referencePath(), "11170", YearMonth.of(2026, 11)))
			.thenReturn(result());

		new WeeklyTradeRefreshSmokeRunner(properties, service).run(null);

		verify(service).refreshOne(properties.mappingPath(),
			properties.referencePath(), "11170", YearMonth.of(2026, 11));
	}

	@Test
	void 원본_충돌이_있는_시험_실행도_종료_오류로_취급하지_않는다() {
		WeeklyTradeRefreshProperties properties = properties(
			false, false, "11170", "202611"
		);
		WeeklyTradeRefreshService service = mock(WeeklyTradeRefreshService.class);
		when(service.refreshOne(properties.mappingPath(),
			properties.referencePath(), "11170", YearMonth.of(2026, 11)))
			.thenReturn(new WeeklyTradeRefreshResult(result().collection(),
				result().link(), result().price(),
				WeeklyTradeRefreshResult.Status.COMPLETE_WITH_CONFLICTS));

		new WeeklyTradeRefreshSmokeRunner(properties, service).run(null);
	}

	@Test
	void 예약_실행이나_잘못된_입력과_함께_시험_실행하지_않는다() {
		WeeklyTradeRefreshService service = mock(WeeklyTradeRefreshService.class);

		assertThatThrownBy(() -> new WeeklyTradeRefreshSmokeRunner(
			properties(false, true, "11170", "202611"), service
		).run(null)).isInstanceOf(IllegalStateException.class);
		assertThatThrownBy(() -> new WeeklyTradeRefreshSmokeRunner(
			properties(false, false, "11170", "202613"), service
		).run(null)).isInstanceOf(IllegalStateException.class);
		verifyNoInteractions(service);
	}

	private static WeeklyTradeRefreshProperties properties(
		boolean runOnStartup, boolean scheduleEnabled,
		String districtCode, String month
	) {
		return new WeeklyTradeRefreshProperties(
			runOnStartup, scheduleEnabled, true, districtCode, month,
			Path.of("mapping.json"), Path.of("dong.tsv"), 3,
			Path.of("weekly.tsv")
		);
	}

	private static WeeklyTradeRefreshResult result() {
		return new WeeklyTradeRefreshResult(
			new WeeklyTradeCollectionResult(List.of(
				new PairResult("11170", "202611", Status.SUCCESS,
					1, 1, 0, 0, 0, null)
			)),
			new ApartmentTradeExactLinkService.Result(
				0, 1, 1, 0, 0, 1, 0, Map.of()
			),
			new ComplexRepresentativePriceResult(1, 1, 1, 0),
			WeeklyTradeRefreshResult.Status.COMPLETE
		);
	}
}
