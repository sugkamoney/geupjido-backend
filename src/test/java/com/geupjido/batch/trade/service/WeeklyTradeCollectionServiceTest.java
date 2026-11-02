package com.geupjido.batch.trade.service;

import java.nio.file.Path;
import java.time.Instant;
import java.time.YearMonth;
import java.util.List;

import org.junit.jupiter.api.Test;

import com.geupjido.batch.trade.exception.ApartmentTradeApiException;
import com.geupjido.batch.trade.exception.ApartmentTradeMappingException;
import com.geupjido.batch.trade.model.ApartmentTradeImportResult;
import com.geupjido.batch.trade.model.ApartmentTradePartialImportResult;
import com.geupjido.batch.trade.model.WeeklyTradeCollectionResult.Status;
import com.geupjido.batch.trade.service.WeeklyTradeTargetPlanner.Target;
import com.geupjido.batch.trade.writer.WeeklyTradeRunRecorder;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

class WeeklyTradeCollectionServiceTest {

	@Test
	void API와_매핑_실패_뒤에도_다음_조합을_수집한다() {
		WeeklyTradeTargetPlanner planner = mock(WeeklyTradeTargetPlanner.class);
		ApartmentTradeImportService importer =
			mock(ApartmentTradeImportService.class);
		WeeklyTradeRunRecorder recorder = mock(WeeklyTradeRunRecorder.class);
		when(recorder.begin()).thenReturn(Instant.parse("2026-11-01T00:00:00Z"));
		Path mappingPath = Path.of("mapping.json");
		YearMonth month = YearMonth.of(2026, 11);
		when(planner.plan(mappingPath, month, 2)).thenReturn(List.of(
			new Target("11110", "202610"),
			new Target("11170", "202610"),
			new Target("11170", "202611"),
			new Target("11110", "202611")
		));
		when(importer.importBackfillMonth("11110", "202610"))
			.thenReturn(partial(2, 1, 0, 0));
		when(importer.importBackfillMonth("11170", "202610"))
			.thenThrow(new ApartmentTradeApiException("API 오류"));
		when(importer.importBackfillMonth("11170", "202611"))
			.thenThrow(new ApartmentTradeMappingException(
				"API 거래의 시군구 또는 계약월이 요청과 다릅니다."));
		when(importer.importBackfillMonth("11110", "202611"))
			.thenReturn(partial(3, 1, 1, 1));

		var result = new WeeklyTradeCollectionService(planner, importer, recorder)
			.collect(mappingPath, month, 2);

		assertThat(result.pairs()).extracting(pair -> pair.status())
			.containsExactly(Status.SUCCESS, Status.FAILED,
				Status.FAILED, Status.PARTIAL);
		assertThat(result.pairs().get(1).failureReason())
			.isEqualTo("API_FAILURE");
		assertThat(result.pairs().get(2).failureReason())
			.isEqualTo("MAPPING_FAILURE");
		assertThat(result.pairs().get(3).sourceTrades()).isEqualTo(4);
		assertThat(result.pairs().get(3).failureReason())
			.isEqualTo("SOURCE_CONFLICT:1");
		assertThat(result.pairs().get(3).newConflictTrades()).isEqualTo(1);
		assertThat(result.count(Status.FAILED)).isEqualTo(2);
		verify(importer).importBackfillMonth("11110", "202611");
		verify(recorder, times(4)).record(
			org.mockito.ArgumentMatchers.any(),
			org.mockito.ArgumentMatchers.any()
		);
	}

	@Test
	void 예상하지_못한_저장_오류는_실패를_숨기지_않고_전파한다() {
		WeeklyTradeTargetPlanner planner = mock(WeeklyTradeTargetPlanner.class);
		ApartmentTradeImportService importer =
			mock(ApartmentTradeImportService.class);
		WeeklyTradeRunRecorder recorder = mock(WeeklyTradeRunRecorder.class);
		Path mappingPath = Path.of("mapping.json");
		YearMonth month = YearMonth.of(2026, 11);
		when(planner.plan(mappingPath, month, 1)).thenReturn(List.of(
			new Target("11110", "202611")
		));
		when(importer.importBackfillMonth("11110", "202611"))
			.thenThrow(new IllegalStateException("DB 연결 실패"));

		assertThatThrownBy(() -> new WeeklyTradeCollectionService(
			planner, importer, recorder
		).collect(mappingPath, month, 1))
			.isInstanceOf(IllegalStateException.class)
			.hasMessage("DB 연결 실패");
	}

	@Test
	void 시험_실행은_매핑에_포함된_시군구_한_건만_수집한다() {
		WeeklyTradeTargetPlanner planner = mock(WeeklyTradeTargetPlanner.class);
		ApartmentTradeImportService importer =
			mock(ApartmentTradeImportService.class);
		WeeklyTradeRunRecorder recorder = mock(WeeklyTradeRunRecorder.class);
		Path mappingPath = Path.of("mapping.json");
		YearMonth month = YearMonth.of(2026, 11);
		when(planner.plan(mappingPath, month, 1)).thenReturn(List.of(
			new Target("11110", "202611"),
			new Target("11170", "202611")
		));
		when(importer.importBackfillMonth("11170", "202611"))
			.thenReturn(partial(1, 1, 0, 0));

		var service = new WeeklyTradeCollectionService(planner, importer, recorder);
		var result = service.collectOne(mappingPath, "11170", month);

		assertThat(result.pairs()).hasSize(1);
		assertThat(result.pairs().getFirst().districtCode()).isEqualTo("11170");
		verify(importer).importBackfillMonth("11170", "202611");
		assertThatThrownBy(() -> service.collectOne(
			mappingPath, "99999", month
		)).isInstanceOf(IllegalArgumentException.class)
			.hasMessageContaining("권역 매핑에 없는");
	}

	private static ApartmentTradePartialImportResult partial(
		int total, int created, int skipped, int conflicts
	) {
		return new ApartmentTradePartialImportResult(
			new ApartmentTradeImportResult(total, created, skipped,
				total - created - skipped),
			conflicts, conflicts, conflicts
		);
	}
}
