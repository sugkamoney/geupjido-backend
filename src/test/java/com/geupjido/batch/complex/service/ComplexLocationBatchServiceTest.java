package com.geupjido.batch.complex.service;

import java.util.List;
import java.util.Optional;

import org.junit.jupiter.api.Test;
import org.springframework.data.domain.PageRequest;

import com.geupjido.batch.complex.exception.MultipleGeocodingResultsException;
import com.geupjido.batch.complex.writer.ComplexLocationRunRecorder;
import com.geupjido.complex.repository.ComplexRepository;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.Mockito.doThrow;
import static org.mockito.Mockito.inOrder;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;

class ComplexLocationBatchServiceTest {

	private final ComplexRepository repository = mock(ComplexRepository.class);
	private final ComplexLocationImportService importService =
		mock(ComplexLocationImportService.class);
	private final ComplexLocationRunRecorder runRecorder =
		mock(ComplexLocationRunRecorder.class);
	private final ComplexLocationBatchService service =
		new ComplexLocationBatchService(repository, importService, runRecorder);

	@Test
	void 제한된_건수를_순서대로_처리하고_다음_커서를_반환한다() {
		String firstId = "A10000001";
		String secondId = "A10000002";
		var first = new ComplexLocationImportService.ProcessResult(
			ComplexLocationImportService.Kind.NEW_LOCATION_SAVED,
			Optional.empty()
		);
		var second = new ComplexLocationImportService.ProcessResult(
			ComplexLocationImportService.Kind.GEOCODING_NOT_FOUND,
			Optional.empty()
		);
		when(repository.findIdsNeedingLocationOrZone("A10000000", PageRequest.of(0, 2)))
			.thenReturn(List.of(firstId, secondId));
		when(importService.processOne(firstId)).thenReturn(first);
		when(importService.processOne(secondId)).thenReturn(second);

		var result = service.processBatch("A10000000", 2);

		assertThat(result.nextAfterId()).isEqualTo(secondId);
		assertThat(result.processed()).containsExactly(
			new ComplexLocationBatchService.ProcessedComplex(firstId, first),
			new ComplexLocationBatchService.ProcessedComplex(secondId, second)
		);
		assertThat(result.failures()).isEmpty();
		verify(runRecorder).recordProcessed(firstId, first);
		verify(runRecorder).recordProcessed(secondId, second);
		var order = inOrder(importService);
		order.verify(importService).processOne(firstId);
		order.verify(importService).processOne(secondId);
	}

	@Test
	void 조회_결과가_없으면_기존_커서를_유지한다() {
		when(repository.findIdsNeedingLocationOrZone("A10000002", PageRequest.of(0, 5)))
			.thenReturn(List.of());

		var result = service.processBatch("A10000002", 5);

		assertThat(result.nextAfterId()).isEqualTo("A10000002");
		assertThat(result.processed()).isEmpty();
		assertThat(result.failures()).isEmpty();
		verifyNoInteractions(importService);
		verify(runRecorder).prepare();
	}

	@Test
	void 복수_지오코딩_결과는_보류하고_다음_단지를_처리한다() {
		String ambiguousId = "A10000001";
		String nextId = "A10000002";
		var nextResult = new ComplexLocationImportService.ProcessResult(
			ComplexLocationImportService.Kind.NEW_LOCATION_SAVED,
			Optional.empty()
		);
		when(repository.findIdsNeedingLocationOrZone("", PageRequest.of(0, 2)))
			.thenReturn(List.of(ambiguousId, nextId));
		when(importService.processOne(ambiguousId))
			.thenThrow(new MultipleGeocodingResultsException());
		when(importService.processOne(nextId)).thenReturn(nextResult);

		var result = service.processBatch(null, 2);

		assertThat(result.failures()).containsExactly(
			new ComplexLocationBatchService.BatchFailure(
				ambiguousId,
				ComplexLocationBatchService.FailureKind.MULTIPLE_GEOCODING_RESULTS
			)
		);
		assertThat(result.processed()).containsExactly(
			new ComplexLocationBatchService.ProcessedComplex(nextId, nextResult)
		);
		assertThat(result.nextAfterId()).isEqualTo(nextId);
		verify(runRecorder).recordFailure(
			ambiguousId,
			ComplexLocationBatchService.FailureKind.MULTIPLE_GEOCODING_RESULTS
		);
		verify(runRecorder).recordProcessed(nextId, nextResult);
		var order = inOrder(importService);
		order.verify(importService).processOne(ambiguousId);
		order.verify(importService).processOne(nextId);
	}

	@Test
	void 허용_건수를_넘으면_조회나_호출을_하지_않는다() {
		assertThatThrownBy(() -> service.processBatch("", 21))
			.isInstanceOf(IllegalArgumentException.class);
		verifyNoInteractions(repository, importService, runRecorder);
	}

	@Test
	void 결과_파일을_준비하지_못하면_단지를_조회하거나_처리하지_않는다() {
		doThrow(new IllegalStateException("파일 오류"))
			.when(runRecorder).prepare();

		assertThatThrownBy(() -> service.processBatch("", 1))
			.isInstanceOf(IllegalStateException.class)
			.hasMessage("파일 오류");
		verifyNoInteractions(repository, importService);
	}

	@Test
	void 처리_실패시_다음_단지를_호출하지_않는다() {
		String firstId = "A10000001";
		String secondId = "A10000002";
		when(repository.findIdsNeedingLocationOrZone("", PageRequest.of(0, 2)))
			.thenReturn(List.of(firstId, secondId));
		when(importService.processOne(firstId))
			.thenThrow(new IllegalStateException("API 오류"));

		assertThatThrownBy(() -> service.processBatch(null, 2))
			.isInstanceOf(IllegalStateException.class)
			.hasMessage("API 오류");
		verify(runRecorder).recordAborted(firstId, "");
		var order = inOrder(importService);
		order.verify(importService).processOne(firstId);
		order.verifyNoMoreInteractions();
	}
}
