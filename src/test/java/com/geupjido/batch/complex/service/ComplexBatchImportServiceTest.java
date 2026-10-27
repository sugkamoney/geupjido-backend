package com.geupjido.batch.complex.service;

import java.util.ArrayList;
import java.util.List;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InOrder;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import com.geupjido.batch.complex.exception.ComplexListApiException;
import com.geupjido.batch.complex.exception.ComplexApiQuotaExceededException;
import com.geupjido.batch.complex.model.ComplexBatchImportResult;
import com.geupjido.batch.complex.model.ComplexBulkImportResult;
import com.geupjido.batch.complex.model.ComplexImportFailure;
import com.geupjido.batch.complex.model.ComplexImportFailureType;
import com.geupjido.batch.complex.model.ComplexLegalDongImportStatus;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.Mockito.inOrder;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoMoreInteractions;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class ComplexBatchImportServiceTest {

	@Mock
	private ComplexBulkImportService bulkImportService;

	private ComplexBatchImportService service;
	private List<Long> retryWaits;

	@BeforeEach
	void setUp() {
		retryWaits = new ArrayList<>();
		service = new ComplexBatchImportService(
			bulkImportService,
			new ComplexApiRetry(retryWaits::add)
		);
	}

	@Test
	void 목록_API_04_오류는_대기_후_재시도한다() {
		String code = "1117013100";
		when(bulkImportService.importByLegalDongCodes(List.of(code)))
			.thenThrow(new ComplexListApiException("HTTP 에러", "04"))
			.thenReturn(new ComplexBulkImportResult(1, 1, 0));

		ComplexBatchImportResult result = service.importByLegalDongCodes(List.of(code));

		assertThat(result.count(ComplexLegalDongImportStatus.SUCCESS)).isEqualTo(1);
		assertThat(retryWaits).containsExactly(2_000L);
		verify(bulkImportService, times(2)).importByLegalDongCodes(List.of(code));
	}

	@Test
	void 목록_API_호출한도_오류는_즉시_전체_수집을_중단한다() {
		String code = "1117013100";
		when(bulkImportService.importByLegalDongCodes(List.of(code)))
			.thenThrow(new ComplexListApiException("호출 한도 초과", "22"));

		assertThatThrownBy(() -> service.importByLegalDongCodes(
			List.of(code, "1120011400")
		))
			.isInstanceOf(ComplexApiQuotaExceededException.class)
			.hasMessageContaining(code);

		assertThat(retryWaits).isEmpty();
		verify(bulkImportService).importByLegalDongCodes(List.of(code));
		verifyNoMoreInteractions(bulkImportService);
	}

	@Test
	void 목록_실패_뒤에도_다음_법정동을_처리하고_재시도_대상을_남긴다() {
		String firstCode = "1117013100";
		String failedCode = "1120011400";
		String partialCode = "1117010100";

		when(bulkImportService.importByLegalDongCodes(List.of(firstCode)))
			.thenReturn(new ComplexBulkImportResult(2, 1, 1));
		when(bulkImportService.importByLegalDongCodes(List.of(failedCode)))
			.thenThrow(new ComplexListApiException("목록 API 오류"));
		when(bulkImportService.importByLegalDongCodes(List.of(partialCode)))
			.thenReturn(new ComplexBulkImportResult(
				2,
				1,
				0,
				List.of(new ComplexImportFailure(
					"A10000001",
					ComplexImportFailureType.API,
					"기본정보 API 오류"
				))
			));

		ComplexBatchImportResult result = service.importByLegalDongCodes(
			List.of(firstCode, failedCode, partialCode)
		);

		assertThat(result.legalDongs()).hasSize(3);
		assertThat(result.legalDongs())
			.extracting(legalDong -> legalDong.status())
			.containsExactly(
				ComplexLegalDongImportStatus.SUCCESS,
				ComplexLegalDongImportStatus.LIST_FAILED,
				ComplexLegalDongImportStatus.PARTIAL
			);
		assertThat(result.legalDongs().get(1).listFailureReason())
			.isEqualTo("목록 API 오류");
		assertThat(result.totalComplexCount()).isEqualTo(4);
		assertThat(result.createdComplexCount()).isEqualTo(2);
		assertThat(result.skippedComplexCount()).isEqualTo(1);
		assertThat(result.failedComplexCount()).isEqualTo(1);
		assertThat(result.retryLegalDongCodes())
			.containsExactly(failedCode, partialCode);

		InOrder inOrder = inOrder(bulkImportService);
		inOrder.verify(bulkImportService)
			.importByLegalDongCodes(List.of(firstCode));
		inOrder.verify(bulkImportService)
			.importByLegalDongCodes(List.of(failedCode));
		inOrder.verify(bulkImportService)
			.importByLegalDongCodes(List.of(partialCode));
	}

	@Test
	void 빈_목록_응답은_실패가_아닌_빈_지역으로_집계한다() {
		String code = "1117013100";
		when(bulkImportService.importByLegalDongCodes(List.of(code)))
			.thenReturn(new ComplexBulkImportResult(0, 0, 0));

		ComplexBatchImportResult result =
			service.importByLegalDongCodes(List.of(code));

		assertThat(result.count(ComplexLegalDongImportStatus.EMPTY))
			.isEqualTo(1L);
		assertThat(result.retryLegalDongCodes()).isEmpty();
	}

	@Test
	void 잘못된_입력은_외부_호출_전에_거부한다() {
		assertThatThrownBy(
			() -> service.importByLegalDongCodes(List.of())
		).isInstanceOf(IllegalArgumentException.class);

		assertThatThrownBy(
			() -> service.importByLegalDongCodes(List.of("11170"))
		).isInstanceOf(IllegalArgumentException.class);

		assertThatThrownBy(
			() -> service.importByLegalDongCodes(
				List.of("1117013100", "1117013100")
			)
		).isInstanceOf(IllegalArgumentException.class);

		verifyNoInteractions(bulkImportService);
	}

	@Test
	void 예상하지_못한_저장_오류는_숨기지_않는다() {
		String code = "1117013100";
		when(bulkImportService.importByLegalDongCodes(List.of(code)))
			.thenThrow(new IllegalStateException("DB 저장 실패"));

		assertThatThrownBy(
			() -> service.importByLegalDongCodes(List.of(code))
		)
			.isInstanceOf(IllegalStateException.class)
			.hasMessage("DB 저장 실패");
	}
}
