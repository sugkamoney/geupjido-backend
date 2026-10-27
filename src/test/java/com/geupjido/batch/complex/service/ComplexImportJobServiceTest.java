package com.geupjido.batch.complex.service;

import java.io.IOException;
import java.math.BigDecimal;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.Future;
import java.util.concurrent.TimeUnit;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import com.geupjido.batch.complex.config.ComplexBulkImportProperties;
import com.geupjido.batch.complex.model.ComplexBatchImportResult;
import com.geupjido.batch.location.model.LocationMapping;
import com.geupjido.batch.location.model.LocationMapping.ZoneDefinition;
import com.geupjido.batch.location.reader.LocationMappingReader;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.anyList;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;

class ComplexImportJobServiceTest {

	@TempDir
	Path tempDir;

	private final LocationMappingReader mappingReader = mock(LocationMappingReader.class);
	private final ComplexBatchImportService batchImportService = mock(ComplexBatchImportService.class);

	@Test
	void 매핑_파일의_법정동_코드를_순서대로_전달한다() throws IOException {
		Path path = createMappingFile();
		when(mappingReader.read(path)).thenReturn(mapping());
		when(batchImportService.importByLegalDongCodes(List.of("1117013100", "1120011400")))
			.thenReturn(new ComplexBatchImportResult(List.of()));

		service(path, List.of()).importComplexes();

		verify(batchImportService).importByLegalDongCodes(List.of("1117013100", "1120011400"));
	}

	@Test
	void 설정한_법정동_코드만_전달한다() throws IOException {
		Path path = createMappingFile();
		when(mappingReader.read(path)).thenReturn(mapping());
		when(batchImportService.importByLegalDongCodes(List.of("1120011400")))
			.thenReturn(new ComplexBatchImportResult(List.of()));

		service(path, List.of("1120011400")).importComplexes();

		verify(batchImportService).importByLegalDongCodes(List.of("1120011400"));
	}

	@Test
	void 월간_실행은_수동_선택_설정과_관계없이_전체_법정동을_전달한다()
		throws IOException {
		Path path = createMappingFile();
		when(mappingReader.read(path)).thenReturn(mapping());
		when(batchImportService.importByLegalDongCodes(List.of("1117013100", "1120011400")))
			.thenReturn(new ComplexBatchImportResult(List.of()));

		service(path, List.of("1117013100")).importAllComplexes();

		verify(batchImportService).importByLegalDongCodes(List.of("1117013100", "1120011400"));
	}

	@Test
	void 매핑에_없는_법정동은_적재_전에_중단한다() throws IOException {
		Path path = createMappingFile();
		when(mappingReader.read(path)).thenReturn(mapping());

		assertThatThrownBy(() -> service(path, List.of("1117010100")).importComplexes())
			.isInstanceOf(IllegalArgumentException.class)
			.hasMessageContaining("권역 매핑에 없는 법정동 코드");

		verifyNoInteractions(batchImportService);
	}

	@Test
	void 매핑_파일_경로가_없으면_실행하지_않는다() {
		assertThatThrownBy(() -> service(null, List.of()).importComplexes())
			.isInstanceOf(IllegalStateException.class)
			.hasMessageContaining("매핑 파일 경로가 설정되지 않았습니다");

		verifyNoInteractions(mappingReader, batchImportService);
	}

	@Test
	void 매핑_파일이_없으면_실행하지_않는다() {
		Path path = tempDir.resolve("missing.json");

		assertThatThrownBy(() -> service(path, List.of()).importComplexes())
			.isInstanceOf(IllegalStateException.class)
			.hasMessageContaining("매핑 파일이 존재하지 않습니다");

		verifyNoInteractions(mappingReader, batchImportService);
	}

	@Test
	void 실행_중인_작업이_있으면_두번째_실행을_거부하고_종료_후_다시_실행한다()
		throws Exception {
		Path path = createMappingFile();
		when(mappingReader.read(path)).thenReturn(mapping());
		ComplexBatchImportResult result = new ComplexBatchImportResult(List.of());
		CountDownLatch entered = new CountDownLatch(1);
		CountDownLatch release = new CountDownLatch(1);
		when(batchImportService.importByLegalDongCodes(anyList()))
			.thenAnswer(invocation -> {
				entered.countDown();
				if (!release.await(5, TimeUnit.SECONDS)) {
					throw new AssertionError("첫 번째 적재가 해제되지 않았습니다.");
				}
				return result;
			})
			.thenReturn(result);

		ComplexImportJobService jobService = service(path, List.of());
		ExecutorService executor = Executors.newSingleThreadExecutor();
		try {
			Future<ComplexBatchImportResult> first =
				executor.submit(jobService::importComplexes);
			assertThat(entered.await(5, TimeUnit.SECONDS)).isTrue();

			assertThatThrownBy(jobService::importComplexes)
				.isInstanceOf(IllegalStateException.class)
				.hasMessageContaining("이미 실행 중");

			release.countDown();
			assertThat(first.get(5, TimeUnit.SECONDS)).isSameAs(result);
			assertThat(jobService.importComplexes()).isSameAs(result);
			verify(batchImportService, times(2)).importByLegalDongCodes(anyList());
		} finally {
			release.countDown();
			executor.shutdownNow();
		}
	}

	@Test
	void 적재가_실패해도_실행_상태를_해제한다() throws IOException {
		Path path = createMappingFile();
		when(mappingReader.read(path)).thenReturn(mapping());
		ComplexBatchImportResult result = new ComplexBatchImportResult(List.of());
		when(batchImportService.importByLegalDongCodes(anyList()))
			.thenThrow(new IllegalStateException("외부 API 오류"))
			.thenReturn(result);

		ComplexImportJobService jobService = service(path, List.of());
		assertThatThrownBy(jobService::importComplexes)
			.isInstanceOf(IllegalStateException.class)
			.hasMessageContaining("외부 API 오류");
		assertThat(jobService.importComplexes()).isSameAs(result);
	}

	private ComplexImportJobService service(Path path, List<String> selectedCodes) {
		return new ComplexImportJobService(
			new ComplexBulkImportProperties(true, path, selectedCodes),
			mappingReader,
			batchImportService
		);
	}

	private Path createMappingFile() throws IOException {
		Path path = tempDir.resolve("mapping.json");
		Files.writeString(path, "{}");
		return path;
	}

	private static LocationMapping mapping() {
		return new LocationMapping(
			List.of(),
			List.of(),
			List.of(
				new ZoneDefinition(
					"yongsan-hannam", "11170", "한남",
					List.of("1117013100"), new BigDecimal("1.5")
				),
				new ZoneDefinition(
					"seongdong-seongsu", "11200", "성수",
					List.of("1120011400"), new BigDecimal("1.8")
				)
			)
		);
	}
}
