package com.geupjido.batch.trade.service;

import java.math.BigDecimal;
import java.nio.file.Files;
import java.nio.file.Path;
import java.time.YearMonth;
import java.util.List;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import com.geupjido.batch.location.model.LocationMapping;
import com.geupjido.batch.location.model.LocationMapping.ZoneDefinition;
import com.geupjido.batch.location.reader.LocationMappingReader;
import com.geupjido.batch.trade.exception.ApartmentTradeApiException;
import com.geupjido.batch.trade.model.ApartmentTradeImportResult;
import com.geupjido.batch.trade.model.ApartmentTradePartialImportResult;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

class ApartmentTradeBackfillServiceTest {

	@TempDir
	Path temporaryDirectory;

	@Test
	void 대상_시군구를_월별_API_수집하고_성공한_조합은_재실행에서_건너뛴다()
		throws Exception {
		LocationMappingReader mappingReader = mock(LocationMappingReader.class);
		ApartmentTradeImportService importService =
			mock(ApartmentTradeImportService.class);
		Path mappingPath = temporaryDirectory.resolve("mapping.json");
		Path manifestPath = temporaryDirectory.resolve("result.tsv");
		when(mappingReader.read(mappingPath)).thenReturn(mapping());
		when(importService.importBackfillMonth("11110", "202609"))
			.thenReturn(partial(2, 2, 0));
		when(importService.importBackfillMonth("11170", "202609"))
			.thenReturn(partial(3, 1, 0));
		when(importService.importBackfillMonth("11110", "202610"))
			.thenReturn(partial(1, 1, 0));
		when(importService.importBackfillMonth("11170", "202610"))
			.thenReturn(partial(0, 0, 0));
		ApartmentTradeBackfillService service =
			new ApartmentTradeBackfillService(mappingReader, importService);

		var first = service.importRange(mappingPath,
			YearMonth.of(2026, 9), YearMonth.of(2026, 10),
			2, manifestPath);
		var second = service.importRange(mappingPath,
			YearMonth.of(2026, 9), YearMonth.of(2026, 10),
			2, manifestPath);

		assertThat(first.plannedPairs()).isEqualTo(4);
		assertThat(first.processedPairs()).isEqualTo(2);
		assertThat(first.createdTrades()).isEqualTo(3);
		assertThat(second.alreadyCompletedPairs()).isEqualTo(2);
		assertThat(second.processedPairs()).isEqualTo(2);
		assertThat(Files.readAllLines(manifestPath)).hasSize(5);
		verify(importService, times(1)).importBackfillMonth("11110", "202609");
		verify(importService, times(1)).importBackfillMonth("11170", "202609");
	}

	@Test
	void 실패한_월은_기록하고_다음_실행에서_다시_시도한다()
		throws Exception {
		LocationMappingReader mappingReader = mock(LocationMappingReader.class);
		ApartmentTradeImportService importService =
			mock(ApartmentTradeImportService.class);
		Path mappingPath = temporaryDirectory.resolve("mapping.json");
		Path manifestPath = temporaryDirectory.resolve("result.tsv");
		when(mappingReader.read(mappingPath)).thenReturn(mapping());
		when(importService.importBackfillMonth("11110", "202609"))
			.thenThrow(new IllegalStateException("API 장애"))
			.thenReturn(partial(1, 1, 0));
		ApartmentTradeBackfillService service =
			new ApartmentTradeBackfillService(mappingReader, importService);

		var failed = service.importRange(mappingPath,
			YearMonth.of(2026, 9), YearMonth.of(2026, 9),
			1, manifestPath);
		var retried = service.importRange(mappingPath,
			YearMonth.of(2026, 9), YearMonth.of(2026, 9),
			1, manifestPath);

		assertThat(failed.failedPairs()).isEqualTo(1);
		assertThat(retried.processedPairs()).isEqualTo(1);
		assertThat(Files.readAllLines(manifestPath))
			.extracting(line -> line.split("\t", -1)[2])
			.containsExactly("상태", "FAILED", "SUCCESS");
		assertThat(Files.readAllLines(manifestPath).get(1))
			.endsWith("\tOTHER_FAILURE");
		verify(importService, times(2)).importBackfillMonth("11110", "202609");
	}

	@Test
	void 기존_일곱_열_결과파일을_업그레이드하고_실패사유를_기록한다()
		throws Exception {
		LocationMappingReader mappingReader = mock(LocationMappingReader.class);
		ApartmentTradeImportService importService =
			mock(ApartmentTradeImportService.class);
		Path mappingPath = temporaryDirectory.resolve("mapping.json");
		Path manifestPath = temporaryDirectory.resolve("legacy.tsv");
		Files.writeString(manifestPath,
			"계약월\t시군구코드\t상태\t원본\t신규\t건너뛰기\t갱신\n"
				+ "202609\t11110\tSUCCESS\t1\t1\t0\t0\n");
		when(mappingReader.read(mappingPath)).thenReturn(mapping());
		when(importService.importBackfillMonth("11170", "202609"))
			.thenThrow(new IllegalStateException(
				"해제된 거래가 정상 거래로 다시 제공되었습니다."));
		ApartmentTradeBackfillService service =
			new ApartmentTradeBackfillService(mappingReader, importService);

		var result = service.importRange(mappingPath,
			YearMonth.of(2026, 9), YearMonth.of(2026, 9),
			1, manifestPath);

		assertThat(result.alreadyCompletedPairs()).isEqualTo(1);
		assertThat(result.failedPairs()).isEqualTo(1);
		assertThat(Files.readAllLines(manifestPath))
			.hasSize(3)
			.allSatisfy(line -> assertThat(line.split("\t", -1)).hasSize(8));
		assertThat(Files.readAllLines(manifestPath).get(2))
			.endsWith("\tCANCELED_TO_NORMAL");
	}

	@Test
	void API_오류_문장은_결과파일에_남기지_않는다() throws Exception {
		LocationMappingReader mappingReader = mock(LocationMappingReader.class);
		ApartmentTradeImportService importService =
			mock(ApartmentTradeImportService.class);
		Path mappingPath = temporaryDirectory.resolve("mapping.json");
		Path manifestPath = temporaryDirectory.resolve("result.tsv");
		when(mappingReader.read(mappingPath)).thenReturn(mapping());
		when(importService.importBackfillMonth("11110", "202609"))
			.thenThrow(new ApartmentTradeApiException("secret-test-value"));
		ApartmentTradeBackfillService service =
			new ApartmentTradeBackfillService(mappingReader, importService);

		service.importRange(mappingPath, YearMonth.of(2026, 9),
			YearMonth.of(2026, 9), 1, manifestPath);

		assertThat(Files.readString(manifestPath))
			.contains("API_FAILURE")
			.doesNotContain("secret-test-value");
	}

	@Test
	void 일부_거래가_보류된_월은_PARTIAL로_기록하고_다시_수집하지_않는다()
		throws Exception {
		LocationMappingReader mappingReader = mock(LocationMappingReader.class);
		ApartmentTradeImportService importService =
			mock(ApartmentTradeImportService.class);
		Path mappingPath = temporaryDirectory.resolve("mapping.json");
		Path manifestPath = temporaryDirectory.resolve("result.tsv");
		when(mappingReader.read(mappingPath)).thenReturn(mapping());
		when(importService.importBackfillMonth("11110", "202609"))
			.thenReturn(partial(3, 2, 2));
		ApartmentTradeBackfillService service =
			new ApartmentTradeBackfillService(mappingReader, importService);

		var first = service.importRange(mappingPath,
			YearMonth.of(2026, 9), YearMonth.of(2026, 9),
			1, manifestPath);
		var second = service.importRange(mappingPath,
			YearMonth.of(2026, 9), YearMonth.of(2026, 9),
			1, manifestPath);

		assertThat(first.conflictTrades()).isEqualTo(2);
		assertThat(second.alreadyCompletedPairs()).isEqualTo(1);
		assertThat(Files.readAllLines(manifestPath).get(1))
			.contains("\tPARTIAL\t5\t2\t1\t0\tSOURCE_CONFLICT:2");
		verify(importService, times(1))
			.importBackfillMonth("11110", "202609");
	}

	private static LocationMapping mapping() {
		return new LocationMapping(List.of(), List.of(), List.of(
			new ZoneDefinition("a", "11170", "a",
				List.of("1117010100"), BigDecimal.ONE),
			new ZoneDefinition("b", "11110", "b",
				List.of("1111010100"), BigDecimal.ONE),
			new ZoneDefinition("c", "11170", "c",
				List.of("1117010200"), BigDecimal.ONE)
		));
	}

	private static ApartmentTradePartialImportResult partial(
		int total, int created, int conflicts
	) {
		return new ApartmentTradePartialImportResult(
			new ApartmentTradeImportResult(
				total, created, total - created, 0
			), conflicts, conflicts == 0 ? 0 : 1
		);
	}
}
