package com.geupjido.batch.complex.service;

import java.util.ArrayList;
import java.util.List;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InOrder;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.web.client.RestClientException;

import com.geupjido.batch.complex.dto.ComplexListApiItem;
import com.geupjido.batch.complex.exception.ComplexBasicInfoApiException;
import com.geupjido.batch.complex.exception.ComplexApiQuotaExceededException;
import com.geupjido.batch.complex.exception.ComplexBasicInfoMappingException;
import com.geupjido.batch.complex.model.ComplexBulkImportResult;
import com.geupjido.batch.complex.model.ComplexImportFailure;
import com.geupjido.batch.complex.model.ComplexImportFailureType;
import com.geupjido.batch.complex.model.ComplexImportStatus;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.Mockito.inOrder;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.verifyNoMoreInteractions;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class ComplexBulkImportServiceTest {

	@Mock
	private ComplexListCollectionService listCollectionService;

	@Mock
	private ComplexBasicInfoImportService basicInfoImportService;

	private ComplexBulkImportService service;
	private List<Long> retryWaits;

	@BeforeEach
	void setUp() {
		retryWaits = new ArrayList<>();
		service = new ComplexBulkImportService(
			listCollectionService,
			basicInfoImportService,
			new ComplexApiRetry(retryWaits::add)
		);
	}

	@Test
	void 단지_목록을_순서대로_처리하고_결과를_집계한다() {
		List<String> legalDongCodes =
			List.of(
				"1117013100",
				"1120011400"
			);

		ComplexListApiItem firstComplex =
			new ComplexListApiItem(
				"A10000001",
				"한남 신규 아파트",
				"1117013100"
			);

		ComplexListApiItem secondComplex =
			new ComplexListApiItem(
				"A10000002",
				"한남 기존 아파트",
				"1117013100"
			);

		ComplexListApiItem thirdComplex =
			new ComplexListApiItem(
				"A10000003",
				"성수 신규 아파트",
				"1120011400"
			);

		when(
			listCollectionService.collectByLegalDongCodes(
				legalDongCodes
			)
		).thenReturn(
			List.of(
				firstComplex,
				secondComplex,
				thirdComplex
			)
		);

		when(
			basicInfoImportService.importIfAbsent("A10000001")
		).thenReturn(ComplexImportStatus.CREATED);

		when(
			basicInfoImportService.importIfAbsent("A10000002")
		).thenReturn(
			ComplexImportStatus.SKIPPED_ALREADY_EXISTS
		);

		when(
			basicInfoImportService.importIfAbsent("A10000003")
		).thenReturn(ComplexImportStatus.CREATED);

		ComplexBulkImportResult result =
			service.importByLegalDongCodes(legalDongCodes);

		assertThat(result)
			.isEqualTo(
				new ComplexBulkImportResult(
					3,
					2,
					1
				)
			);

		InOrder inOrder = inOrder(
			listCollectionService,
			basicInfoImportService
		);
		inOrder.verify(listCollectionService)
			.collectByLegalDongCodes(legalDongCodes);
		inOrder.verify(basicInfoImportService)
			.importIfAbsent("A10000001");
		inOrder.verify(basicInfoImportService)
			.importIfAbsent("A10000002");
		inOrder.verify(basicInfoImportService)
			.importIfAbsent("A10000003");
	}

	@Test
	void 수집된_단지가_없으면_빈_결과를_반환한다() {
		List<String> legalDongCodes =
			List.of("1117013100");

		when(
			listCollectionService.collectByLegalDongCodes(
				legalDongCodes
			)
		).thenReturn(List.of());

		ComplexBulkImportResult result =
			service.importByLegalDongCodes(legalDongCodes);

		assertThat(result)
			.isEqualTo(
				new ComplexBulkImportResult(
					0,
					0,
					0
				)
			);

		verifyNoInteractions(basicInfoImportService);
	}

	@Test
	void API_호출이_한_번_실패한_뒤_성공하면_신규_건수로_집계한다() {
		List<String> legalDongCodes =
			List.of("1117013100");

		ComplexListApiItem complex =
			new ComplexListApiItem(
				"A10000001",
				"한남 테스트 아파트",
				"1117013100"
			);

		when(
			listCollectionService.collectByLegalDongCodes(
				legalDongCodes
			)
		).thenReturn(List.of(complex));

		when(
			basicInfoImportService.importIfAbsent("A10000001")
		)
			.thenThrow(
				new ComplexBasicInfoApiException(
					"첫 번째 API 호출에 실패했습니다.",
					"04"
				)
			)
			.thenReturn(ComplexImportStatus.CREATED);

		ComplexBulkImportResult result =
			service.importByLegalDongCodes(legalDongCodes);

		assertThat(result)
			.isEqualTo(
				new ComplexBulkImportResult(
					1,
					1,
					0
				)
			);
		assertThat(retryWaits).containsExactly(2_000L);

		InOrder inOrder = inOrder(
			listCollectionService,
			basicInfoImportService
		);
		inOrder.verify(listCollectionService)
			.collectByLegalDongCodes(legalDongCodes);
		inOrder.verify(
			basicInfoImportService,
			times(2)
		)
			.importIfAbsent("A10000001");

		verifyNoMoreInteractions(
			listCollectionService,
			basicInfoImportService
		);
	}

	@Test
	void 일시적인_연결_오류는_기본정보를_다시_조회한다() {
		String legalDongCode = "1117013100";
		when(listCollectionService.collectByLegalDongCodes(List.of(legalDongCode)))
			.thenReturn(List.of(new ComplexListApiItem(
				"A10000001", "한남 테스트 아파트", legalDongCode
			)));
		when(basicInfoImportService.importIfAbsent("A10000001"))
			.thenThrow(new ComplexBasicInfoApiException(
				"공동주택 단지 기본정보 API 호출에 실패했습니다.",
				new RestClientException("연결 실패")
			))
			.thenReturn(ComplexImportStatus.CREATED);

		ComplexBulkImportResult result = service.importByLegalDongCodes(
			List.of(legalDongCode)
		);

		assertThat(result.createdCount()).isEqualTo(1);
		assertThat(retryWaits).containsExactly(2_000L);
		verify(basicInfoImportService, times(2)).importIfAbsent("A10000001");
	}

	@Test
	void API_호출이_두_번_실패한_뒤_성공하면_건너뛰기_건수로_집계한다() {
		List<String> legalDongCodes =
			List.of("1117013100");

		ComplexListApiItem complex =
			new ComplexListApiItem(
				"A10000001",
				"한남 기존 아파트",
				"1117013100"
			);

		when(
			listCollectionService.collectByLegalDongCodes(
				legalDongCodes
			)
		).thenReturn(List.of(complex));

		when(
			basicInfoImportService.importIfAbsent("A10000001")
		)
			.thenThrow(
				new ComplexBasicInfoApiException(
					"첫 번째 API 호출에 실패했습니다.",
					"04"
				),
				new ComplexBasicInfoApiException(
					"두 번째 API 호출에 실패했습니다.",
					"04"
				)
			)
			.thenReturn(
				ComplexImportStatus.SKIPPED_ALREADY_EXISTS
			);

		ComplexBulkImportResult result =
			service.importByLegalDongCodes(legalDongCodes);

		assertThat(result)
			.isEqualTo(
				new ComplexBulkImportResult(
					1,
					0,
					1
				)
			);
		assertThat(retryWaits).containsExactly(2_000L, 4_000L);

		verify(
			basicInfoImportService,
			times(3)
		)
			.importIfAbsent("A10000001");
	}

	@Test
	void 중간_단지_API_호출이_실패해도_이후_단지를_처리한다() {
		List<String> legalDongCodes =
			List.of("1117013100");

		ComplexListApiItem firstComplex =
			new ComplexListApiItem(
				"A10000001",
				"첫 번째 아파트",
				"1117013100"
			);

		ComplexListApiItem secondComplex =
			new ComplexListApiItem(
				"A10000002",
				"두 번째 아파트",
				"1117013100"
			);

		ComplexListApiItem thirdComplex =
			new ComplexListApiItem(
				"A10000003",
				"세 번째 아파트",
				"1117013100"
			);

		when(
			listCollectionService.collectByLegalDongCodes(
				legalDongCodes
			)
		).thenReturn(
			List.of(
				firstComplex,
				secondComplex,
				thirdComplex
			)
		);

		when(
			basicInfoImportService.importIfAbsent("A10000001")
		).thenReturn(ComplexImportStatus.CREATED);

		when(
			basicInfoImportService.importIfAbsent("A10000002")
		).thenThrow(
			new ComplexBasicInfoApiException(
				"공동주택 단지 기본정보 API 호출에 실패했습니다.",
				"10"
			)
		);

		when(
			basicInfoImportService.importIfAbsent("A10000003")
		).thenReturn(ComplexImportStatus.CREATED);

		ComplexBulkImportResult result =
			service.importByLegalDongCodes(legalDongCodes);

		assertThat(result)
			.isEqualTo(
				new ComplexBulkImportResult(
					3,
					2,
					0,
					List.of(
						new ComplexImportFailure(
							"A10000002",
							ComplexImportFailureType.API,
							"공동주택 단지 기본정보 API 호출에 실패했습니다."
						)
					)
				)
			);

		InOrder inOrder = inOrder(
			listCollectionService,
			basicInfoImportService
		);
		inOrder.verify(listCollectionService)
			.collectByLegalDongCodes(legalDongCodes);
		inOrder.verify(basicInfoImportService)
			.importIfAbsent("A10000001");
		inOrder.verify(
			basicInfoImportService,
			times(1)
		)
			.importIfAbsent("A10000002");
		inOrder.verify(basicInfoImportService)
			.importIfAbsent("A10000003");

		verifyNoMoreInteractions(
			listCollectionService,
			basicInfoImportService
		);
		assertThat(retryWaits).isEmpty();
	}

	@Test
	void 기본정보_API_호출한도_오류는_뒤_단지를_조회하지_않고_중단한다() {
		String legalDongCode = "1117013100";
		when(listCollectionService.collectByLegalDongCodes(List.of(legalDongCode)))
			.thenReturn(List.of(
				new ComplexListApiItem("A10000001", "첫 번째", legalDongCode),
				new ComplexListApiItem("A10000002", "두 번째", legalDongCode)
			));
		when(basicInfoImportService.importIfAbsent("A10000001"))
			.thenThrow(new ComplexBasicInfoApiException("호출 한도 초과", "22"));

		assertThatThrownBy(() -> service.importByLegalDongCodes(List.of(legalDongCode)))
			.isInstanceOf(ComplexApiQuotaExceededException.class)
			.hasMessageContaining("A10000001");

		assertThat(retryWaits).isEmpty();
		verify(basicInfoImportService).importIfAbsent("A10000001");
		verifyNoMoreInteractions(basicInfoImportService);
	}

	@Test
	void 중간_단지_매핑이_실패해도_이후_단지를_처리한다() {
		List<String> legalDongCodes =
			List.of("1117013100");

		ComplexListApiItem firstComplex =
			new ComplexListApiItem(
				"A10000001",
				"첫 번째 아파트",
				"1117013100"
			);

		ComplexListApiItem secondComplex =
			new ComplexListApiItem(
				"A10000002",
				"두 번째 아파트",
				"1117013100"
			);

		ComplexListApiItem thirdComplex =
			new ComplexListApiItem(
				"A10000003",
				"세 번째 아파트",
				"1117013100"
			);

		when(
			listCollectionService.collectByLegalDongCodes(
				legalDongCodes
			)
		).thenReturn(
			List.of(
				firstComplex,
				secondComplex,
				thirdComplex
			)
		);

		when(
			basicInfoImportService.importIfAbsent("A10000001")
		).thenReturn(ComplexImportStatus.CREATED);

		when(
			basicInfoImportService.importIfAbsent("A10000002")
		).thenThrow(
			new ComplexBasicInfoMappingException(
				"단지 기본정보 변환에 실패했습니다."
			)
		);

		when(
			basicInfoImportService.importIfAbsent("A10000003")
		).thenReturn(
			ComplexImportStatus.SKIPPED_ALREADY_EXISTS
		);

		ComplexBulkImportResult result =
			service.importByLegalDongCodes(legalDongCodes);

		assertThat(result)
			.isEqualTo(
				new ComplexBulkImportResult(
					3,
					1,
					1,
					List.of(
						new ComplexImportFailure(
							"A10000002",
							ComplexImportFailureType.MAPPING,
							"단지 기본정보 변환에 실패했습니다."
						)
					)
				)
			);

		InOrder inOrder = inOrder(
			listCollectionService,
			basicInfoImportService
		);
		inOrder.verify(listCollectionService)
			.collectByLegalDongCodes(legalDongCodes);
		inOrder.verify(basicInfoImportService)
			.importIfAbsent("A10000001");
		inOrder.verify(basicInfoImportService)
			.importIfAbsent("A10000002");
		inOrder.verify(basicInfoImportService)
			.importIfAbsent("A10000003");

		verifyNoMoreInteractions(
			listCollectionService,
			basicInfoImportService
		);
	}

	@Test
	void 매핑_예외의_메시지가_없으면_예외_이름을_실패_사유로_사용한다() {
		List<String> legalDongCodes =
			List.of("1117013100");

		ComplexListApiItem complex =
			new ComplexListApiItem(
				"A10000001",
				"한남 테스트 아파트",
				"1117013100"
			);

		when(
			listCollectionService.collectByLegalDongCodes(
				legalDongCodes
			)
		).thenReturn(List.of(complex));

		when(
			basicInfoImportService.importIfAbsent("A10000001")
		).thenThrow(new IllegalArgumentException());

		ComplexBulkImportResult result =
			service.importByLegalDongCodes(legalDongCodes);

		assertThat(result)
			.isEqualTo(
				new ComplexBulkImportResult(
					1,
					0,
					0,
					List.of(
						new ComplexImportFailure(
							"A10000001",
							ComplexImportFailureType.MAPPING,
							"IllegalArgumentException"
						)
					)
				)
			);
	}

	@Test
	void 예상하지_못한_오류는_재시도하지_않고_상위로_전파한다() {
		List<String> legalDongCodes =
			List.of("1117013100");

		ComplexListApiItem complex =
			new ComplexListApiItem(
				"A10000001",
				"한남 테스트 아파트",
				"1117013100"
			);

		when(
			listCollectionService.collectByLegalDongCodes(
				legalDongCodes
			)
		).thenReturn(List.of(complex));

		when(
			basicInfoImportService.importIfAbsent("A10000001")
		).thenThrow(
			new IllegalStateException(
				"예상하지 못한 저장 오류입니다."
			)
		);

		assertThatThrownBy(
			() -> service.importByLegalDongCodes(
				legalDongCodes
			)
		)
			.isInstanceOf(IllegalStateException.class)
			.hasMessage(
				"예상하지 못한 저장 오류입니다."
			);

		verify(
			basicInfoImportService,
			times(1)
		)
			.importIfAbsent("A10000001");
	}
}
