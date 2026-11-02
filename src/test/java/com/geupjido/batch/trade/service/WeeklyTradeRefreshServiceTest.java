package com.geupjido.batch.trade.service;

import java.nio.file.Files;
import java.nio.file.Path;
import java.time.YearMonth;
import java.util.List;
import java.util.Map;
import java.util.Set;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;
import org.mockito.InOrder;

import com.geupjido.batch.trade.model.ComplexRepresentativePriceResult;
import com.geupjido.batch.trade.model.WeeklyTradeCollectionResult;
import com.geupjido.batch.trade.model.WeeklyTradeCollectionResult.PairResult;
import com.geupjido.batch.trade.model.WeeklyTradeCollectionResult.Status;
import com.geupjido.batch.trade.model.WeeklyTradeRefreshResult;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.Mockito.inOrder;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;

class WeeklyTradeRefreshServiceTest {

	@TempDir
	Path temporaryDirectory;

	@Test
	void 일부_API_수집이_실패해도_성공_거래를_연결하고_가격을_갱신한다()
		throws Exception {
		Path mapping = Files.createFile(temporaryDirectory.resolve("mapping.json"));
		Path reference = Files.createFile(temporaryDirectory.resolve("dong.tsv"));
		YearMonth month = YearMonth.of(2026, 11);
		WeeklyTradeCollectionService collector = mock(WeeklyTradeCollectionService.class);
		ApartmentTradeExactLinkService linker = mock(ApartmentTradeExactLinkService.class);
		ComplexRepresentativePriceService pricing =
			mock(ComplexRepresentativePriceService.class);
		when(collector.collect(mapping, month, 2)).thenReturn(new WeeklyTradeCollectionResult(
			List.of(pair(Status.SUCCESS), pair(Status.FAILED))
		));
		when(linker.linkExact(reference, mapping, pairs())).thenReturn(
			link(0, 0, 0, 0));
		when(pricing.recalculateAll()).thenReturn(
			new ComplexRepresentativePriceResult(2, 2, 2, 0)
		);

		WeeklyTradeRefreshResult result = new WeeklyTradeRefreshService(
			collector, linker, pricing
		).refresh(mapping, reference, month, 2);

		InOrder order = inOrder(collector, linker, pricing);
		order.verify(collector).collect(mapping, month, 2);
		order.verify(linker).linkExact(reference, mapping, pairs());
		order.verify(pricing).recalculateAll();
		assertThat(result.status())
			.isEqualTo(WeeklyTradeRefreshResult.Status.FAILED);
		assertThat(result.price().pricedComplexCount()).isEqualTo(2);
	}

	@Test
	void 원본_충돌만_있으면_수집_완료와_충돌을_함께_표시한다()
		throws Exception {
		Path mapping = Files.createFile(temporaryDirectory.resolve("mapping-partial.json"));
		Path reference = Files.createFile(temporaryDirectory.resolve("dong-partial.tsv"));
		YearMonth month = YearMonth.of(2026, 11);
		WeeklyTradeCollectionService collector = mock(WeeklyTradeCollectionService.class);
		ApartmentTradeExactLinkService linker = mock(ApartmentTradeExactLinkService.class);
		ComplexRepresentativePriceService pricing =
			mock(ComplexRepresentativePriceService.class);
		when(collector.collect(mapping, month, 2)).thenReturn(
			new WeeklyTradeCollectionResult(List.of(pair(Status.PARTIAL))));
		when(linker.linkExact(reference, mapping, pairs())).thenReturn(
			link(0, 0, 0, 0));
		when(pricing.recalculateAll()).thenReturn(
			new ComplexRepresentativePriceResult(2, 2, 2, 0));

		var result = new WeeklyTradeRefreshService(collector, linker, pricing)
			.refresh(mapping, reference, month, 2);

		assertThat(result.status()).isEqualTo(
			WeeklyTradeRefreshResult.Status.COMPLETE_WITH_CONFLICTS);
	}

	@Test
	void 전부_수집되고_연결_이상도_없으면_완료로_판정한다()
		throws Exception {
		Path mapping = Files.createFile(temporaryDirectory.resolve("mapping.json"));
		Path reference = Files.createFile(temporaryDirectory.resolve("dong.tsv"));
		YearMonth month = YearMonth.of(2026, 11);
		WeeklyTradeCollectionService collector = mock(WeeklyTradeCollectionService.class);
		ApartmentTradeExactLinkService linker = mock(ApartmentTradeExactLinkService.class);
		ComplexRepresentativePriceService pricing =
			mock(ComplexRepresentativePriceService.class);
		when(collector.collect(mapping, month, 2)).thenReturn(
			new WeeklyTradeCollectionResult(List.of(pair(Status.SUCCESS)))
		);
		when(linker.linkExact(reference, mapping, pairs())).thenReturn(
			link(8, 0, 0, 0));
		when(pricing.recalculateAll()).thenReturn(
			new ComplexRepresentativePriceResult(2, 2, 2, 0)
		);

		var result = new WeeklyTradeRefreshService(collector, linker, pricing)
			.refresh(mapping, reference, month, 2);

		assertThat(result.status())
			.isEqualTo(WeeklyTradeRefreshResult.Status.COMPLETE);
		assertThat(result.link().unknown()).isEqualTo(8);
	}

	@Test
	void 이번_수집_법정동에_미확인이_있으면_검토_대상이다()
		throws Exception {
		Path mapping = Files.createFile(temporaryDirectory.resolve("mapping.json"));
		Path reference = Files.createFile(temporaryDirectory.resolve("dong.tsv"));
		WeeklyTradeCollectionService collector = mock(WeeklyTradeCollectionService.class);
		ApartmentTradeExactLinkService linker = mock(ApartmentTradeExactLinkService.class);
		ComplexRepresentativePriceService pricing =
			mock(ComplexRepresentativePriceService.class);
		when(collector.collectOne(mapping, "11170", YearMonth.of(2026, 11)))
			.thenReturn(new WeeklyTradeCollectionResult(
				List.of(pair("11170", Status.SUCCESS))));
		when(linker.linkExact(reference, mapping, pairs("11170"))).thenReturn(
			link(8, 0, 1, 0));
		when(pricing.recalculateAll()).thenReturn(
			new ComplexRepresentativePriceResult(2, 2, 2, 0));

		var result = new WeeklyTradeRefreshService(collector, linker, pricing)
			.refreshOne(mapping, reference, "11170", YearMonth.of(2026, 11));

		assertThat(result.status())
			.isEqualTo(WeeklyTradeRefreshResult.Status.NEEDS_REVIEW);
	}

	@Test
	void 참조_파일이_없으면_API_수집을_시작하지_않는다() throws Exception {
		Path mapping = Files.createFile(temporaryDirectory.resolve("mapping.json"));
		WeeklyTradeCollectionService collector = mock(WeeklyTradeCollectionService.class);
		WeeklyTradeRefreshService service = new WeeklyTradeRefreshService(
			collector,
			mock(ApartmentTradeExactLinkService.class),
			mock(ComplexRepresentativePriceService.class)
		);

		assertThatThrownBy(() -> service.refresh(mapping,
			temporaryDirectory.resolve("missing.tsv"),
			YearMonth.of(2026, 11), 2
		)).isInstanceOf(IllegalStateException.class);
		verifyNoInteractions(collector);
	}

	@Test
	void 시험_실행도_한_시군구를_수집한_뒤_연결과_가격을_갱신한다()
		throws Exception {
		Path mapping = Files.createFile(temporaryDirectory.resolve("mapping.json"));
		Path reference = Files.createFile(temporaryDirectory.resolve("dong.tsv"));
		YearMonth month = YearMonth.of(2026, 11);
		WeeklyTradeCollectionService collector = mock(WeeklyTradeCollectionService.class);
		ApartmentTradeExactLinkService linker = mock(ApartmentTradeExactLinkService.class);
		ComplexRepresentativePriceService pricing =
			mock(ComplexRepresentativePriceService.class);
		when(collector.collectOne(mapping, "11170", month)).thenReturn(
			new WeeklyTradeCollectionResult(List.of(
				pair("11170", Status.SUCCESS)))
		);
		when(linker.linkExact(reference, mapping, pairs("11170"))).thenReturn(
			link(0, 0, 0, 0));
		when(pricing.recalculateAll()).thenReturn(
			new ComplexRepresentativePriceResult(2, 2, 2, 0)
		);

		var result = new WeeklyTradeRefreshService(collector, linker, pricing)
			.refreshOne(mapping, reference, "11170", month);

		InOrder order = inOrder(collector, linker, pricing);
		order.verify(collector).collectOne(mapping, "11170", month);
		order.verify(linker).linkExact(reference, mapping, pairs("11170"));
		order.verify(pricing).recalculateAll();
		assertThat(result.status())
			.isEqualTo(WeeklyTradeRefreshResult.Status.COMPLETE);
	}

	private static PairResult pair(Status status) {
		return pair("11110", status);
	}

	private static PairResult pair(String districtCode, Status status) {
		return new PairResult(districtCode, "202611", status,
			1, 1, 0, 0, 0,
			status == Status.FAILED ? "API_FAILURE" : null);
	}

	private static Set<ApartmentTradeExactLinkService.TradeMonth> pairs() {
		return pairs("11110");
	}

	private static Set<ApartmentTradeExactLinkService.TradeMonth> pairs(
		String districtCode
	) {
		return Set.of(new ApartmentTradeExactLinkService.TradeMonth(
			districtCode, "202611"));
	}

	private static ApartmentTradeExactLinkService.Result link(
		long unknown, long zoneMismatch, long currentUnknown,
		long currentZoneMismatch
	) {
		return new ApartmentTradeExactLinkService.Result(
			0, 2, 2, 0, unknown, 1, zoneMismatch, Map.of(),
			currentUnknown, currentZoneMismatch
		);
	}
}
