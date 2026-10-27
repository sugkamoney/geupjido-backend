package com.geupjido.batch.complex.service;

import java.util.List;
import java.util.Optional;

import org.junit.jupiter.api.Test;

import com.geupjido.batch.complex.service.ComplexLocationBatchService.BatchFailure;
import com.geupjido.batch.complex.service.ComplexLocationBatchService.BatchResult;
import com.geupjido.batch.complex.service.ComplexLocationBatchService.FailureKind;
import com.geupjido.batch.complex.service.ComplexLocationBatchService.ProcessedComplex;
import com.geupjido.batch.complex.service.ComplexLocationImportService.Kind;
import com.geupjido.batch.complex.service.ComplexLocationImportService.ProcessResult;
import com.geupjido.batch.complex.service.ComplexZoneMatchService.MatchResult;
import com.geupjido.batch.complex.service.ComplexZoneMatchService.Status;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

class ComplexLocationJobServiceTest {

	private final ComplexLocationBatchService batchService =
		mock(ComplexLocationBatchService.class);
	private final ComplexLocationJobService service =
		new ComplexLocationJobService(batchService);

	@Test
	void 전체_미처리를_커서로_이어가며_보류와_미배정을_집계한다() {
		ProcessResult matched = new ProcessResult(
			Kind.NEW_LOCATION_SAVED,
			Optional.of(new MatchResult(Status.MATCHED, List.of()))
		);
		ProcessResult outside = new ProcessResult(
			Kind.NEW_LOCATION_SAVED,
			Optional.of(new MatchResult(Status.OUTSIDE, List.of()))
		);
		when(batchService.processBatch("", 20)).thenReturn(new BatchResult(
			"A3",
			List.of(new ProcessedComplex("A1", matched),
				new ProcessedComplex("A2", outside)),
			List.of(new BatchFailure("A3", FailureKind.MULTIPLE_GEOCODING_RESULTS))
		));
		when(batchService.processBatch("A3", 20)).thenReturn(
			new BatchResult("A3", List.of(), List.of())
		);

		ComplexLocationJobService.JobResult result = service.processPending();

		assertThat(result).isEqualTo(
			new ComplexLocationJobService.JobResult(2, 1, 1, 1, 0, "A3")
		);
		verify(batchService).processBatch("A3", 20);
	}
}
