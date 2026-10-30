package com.geupjido.batch.complex.service;

import java.time.LocalDate;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InOrder;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.dao.DataAccessResourceFailureException;

import com.geupjido.batch.complex.exception.ComplexBasicInfoApiException;
import com.geupjido.batch.complex.model.ComplexImportStatus;
import com.geupjido.complex.domain.Complex;
import com.geupjido.complex.repository.ComplexRepository;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.Mockito.inOrder;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoMoreInteractions;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class ComplexBasicInfoImportServiceTest {

	@Mock
	private ComplexBasicInfoCollectionService collectionService;

	@Mock
	private ComplexRepository complexRepository;

	@InjectMocks
	private ComplexBasicInfoImportService importService;

	@Test
	void 신규_단지를_수집해_저장한다() {
		Complex complex = new Complex(
			"A10027875",
			"괴정 경성스마트W아파트",
			"부산광역시 사하구 괴정동 258",
			"부산광역시 사하구 낙동대로 180",
			"2638010100",
			182,
			3,
			LocalDate.of(2015, 8, 6)
		);
		complex.updateSaleType("분양");

		when(complexRepository.existsById("A10027875"))
			.thenReturn(false);
		when(
			collectionService.collectByComplexCode("A10027875")
		)
			.thenReturn(complex);

		ComplexImportStatus status =
			importService.importIfAbsent("A10027875");

		assertThat(status)
			.isEqualTo(ComplexImportStatus.CREATED);

		InOrder inOrder = inOrder(
			complexRepository,
			collectionService
		);
		inOrder.verify(complexRepository)
			.existsById("A10027875");
		inOrder.verify(collectionService)
			.collectByComplexCode("A10027875");
		inOrder.verify(complexRepository).save(complex);
	}

	@Test
	void 기존_단지는_수집하지_않고_건너뛴다() {
		when(complexRepository.existsById("A10027875"))
			.thenReturn(true);

		ComplexImportStatus status =
			importService.importIfAbsent("A10027875");

		assertThat(status)
			.isEqualTo(
				ComplexImportStatus.SKIPPED_ALREADY_EXISTS
			);

		verify(complexRepository)
			.existsById("A10027875");
		verifyNoMoreInteractions(
			complexRepository,
			collectionService
		);
	}

	@Test
	void 임대_전용_단지는_저장하지_않는다() {
		Complex complex = new Complex("A1", "임대단지", "서울 중구 신당동 844",
			null, "1114016200", 100, null, LocalDate.of(2002, 5, 25));
		complex.updateSaleType("임대");
		when(complexRepository.existsById("A1")).thenReturn(false);
		when(collectionService.collectByComplexCode("A1")).thenReturn(complex);

		assertThat(importService.importIfAbsent("A1"))
			.isEqualTo(ComplexImportStatus.SKIPPED_RENTAL_ONLY);
		verify(complexRepository).existsById("A1");
		verify(collectionService).collectByComplexCode("A1");
		verifyNoMoreInteractions(complexRepository, collectionService);
	}

	@Test
	void 사택_관사_단지는_저장하지_않는다() {
		Complex complex = new Complex("A1", "사택단지", "서울 중구 신당동 844",
			null, "1114016200", 100, null, LocalDate.of(2002, 5, 25));
		complex.updateSaleType("사택 및 관사 등");
		when(complexRepository.existsById("A1")).thenReturn(false);
		when(collectionService.collectByComplexCode("A1")).thenReturn(complex);

		assertThat(importService.importIfAbsent("A1"))
			.isEqualTo(ComplexImportStatus.SKIPPED_STAFF_HOUSING);
		verify(complexRepository).existsById("A1");
		verify(collectionService).collectByComplexCode("A1");
		verifyNoMoreInteractions(complexRepository, collectionService);
	}

	@Test
	void 분양형태_미확인_단지는_저장하지_않는다() {
		Complex complex = new Complex("A1", "미확인단지", "서울 중구 신당동 844",
			null, "1114016200", 100, null, LocalDate.of(2002, 5, 25));
		when(complexRepository.existsById("A1")).thenReturn(false);
		when(collectionService.collectByComplexCode("A1")).thenReturn(complex);

		assertThat(importService.importIfAbsent("A1"))
			.isEqualTo(ComplexImportStatus.SKIPPED_SALE_TYPE_UNKNOWN);
		verify(complexRepository).existsById("A1");
		verify(collectionService).collectByComplexCode("A1");
		verifyNoMoreInteractions(complexRepository, collectionService);
	}

	@Test
	void 단지_수집이_실패하면_저장하지_않는다() {
		when(complexRepository.existsById("A10027875"))
			.thenReturn(false);
		when(
			collectionService.collectByComplexCode("A10027875")
		)
			.thenThrow(
				new ComplexBasicInfoApiException(
					"공동주택 단지 기본정보 API 호출에 실패했습니다."
				)
			);

		assertThatThrownBy(
			() -> importService.importIfAbsent("A10027875")
		)
			.isInstanceOf(ComplexBasicInfoApiException.class)
			.hasMessageContaining(
				"공동주택 단지 기본정보 API 호출에 실패했습니다"
			);

		verify(complexRepository)
			.existsById("A10027875");
		verify(collectionService)
			.collectByComplexCode("A10027875");
		verifyNoMoreInteractions(
			complexRepository,
			collectionService
		);
	}

	@Test
	void DB_저장이_실패하면_예외를_그대로_전달한다() {
		Complex complex = new Complex(
			"A10027875",
			"괴정 경성스마트W아파트",
			"부산광역시 사하구 괴정동 258",
			null,
			"2638010100",
			182,
			3,
			LocalDate.of(2015, 8, 6)
		);
		complex.updateSaleType("분양");

		when(complexRepository.existsById("A10027875"))
			.thenReturn(false);
		when(
			collectionService.collectByComplexCode("A10027875")
		)
			.thenReturn(complex);
		when(complexRepository.save(complex))
			.thenThrow(
				new DataAccessResourceFailureException(
					"데이터베이스 연결에 실패했습니다."
				)
			);

		assertThatThrownBy(
			() -> importService.importIfAbsent("A10027875")
		)
			.isInstanceOf(
				DataAccessResourceFailureException.class
			)
			.hasMessageContaining(
				"데이터베이스 연결에 실패했습니다"
			);
	}
}
