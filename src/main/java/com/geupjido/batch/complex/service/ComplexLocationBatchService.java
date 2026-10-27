package com.geupjido.batch.complex.service;

import java.util.ArrayList;
import java.util.List;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.data.domain.PageRequest;
import org.springframework.stereotype.Service;

import com.geupjido.batch.complex.exception.MultipleGeocodingResultsException;
import com.geupjido.batch.complex.writer.ComplexLocationRunRecorder;
import com.geupjido.complex.repository.ComplexRepository;

@Service
@ConditionalOnProperty(
	prefix = "app.external.geocoding",
	name = "enabled",
	havingValue = "true"
)
public class ComplexLocationBatchService {

	private static final Logger log =
		LoggerFactory.getLogger(ComplexLocationBatchService.class);

	private final ComplexRepository complexRepository;
	private final ComplexLocationImportService importService;
	private final ComplexLocationRunRecorder runRecorder;

	public ComplexLocationBatchService(
		ComplexRepository complexRepository,
		ComplexLocationImportService importService,
		ComplexLocationRunRecorder runRecorder
	) {
		this.complexRepository = complexRepository;
		this.importService = importService;
		this.runRecorder = runRecorder;
	}

	public BatchResult processBatch(String afterId, int maxCount) {
		if (maxCount < 1 || maxCount > 20) {
			throw new IllegalArgumentException(
				"한 번에 처리할 단지 수는 1~20이어야 합니다."
			);
		}

		runRecorder.prepare();
		String cursor = afterId == null ? "" : afterId;
		List<String> ids = complexRepository.findIdsNeedingLocationOrZone(
			cursor,
			PageRequest.of(0, maxCount)
		);

		List<ProcessedComplex> processed = new ArrayList<>();
		List<BatchFailure> failures = new ArrayList<>();

		for (String id : ids) {
			log.info("단지 좌표·권역 처리 시작: 단지코드={}, 이전 커서={}", id, cursor);

			try {
				ComplexLocationImportService.ProcessResult result =
					importService.processOne(id);

				runRecorder.recordProcessed(id, result);
				processed.add(new ProcessedComplex(id, result));
				cursor = id;

				log.info(
					"단지 좌표·권역 처리 완료: 단지코드={}, 처리={}, 다음 커서={}",
					id, result.kind(), cursor
				);
			} catch (MultipleGeocodingResultsException ex) {
				runRecorder.recordFailure(
					id, FailureKind.MULTIPLE_GEOCODING_RESULTS
				);
				failures.add(new BatchFailure(
					id, FailureKind.MULTIPLE_GEOCODING_RESULTS
				));
				cursor = id;
				log.warn(
					"단지 좌표·권역 처리를 보류합니다. 단지코드={}, 사유={}, 다음 커서={}",
					id, FailureKind.MULTIPLE_GEOCODING_RESULTS, cursor
				);
			} catch (RuntimeException ex) {
				try {
					runRecorder.recordAborted(id, cursor);
				} catch (RuntimeException recordFailure) {
					ex.addSuppressed(recordFailure);
				}
				throw ex;
			}
		}

		return new BatchResult(
			cursor, List.copyOf(processed), List.copyOf(failures)
		);
	}

	public enum FailureKind {
		MULTIPLE_GEOCODING_RESULTS
	}

	public record BatchFailure(String complexId, FailureKind kind) {
	}

	public record ProcessedComplex(
		String complexId,
		ComplexLocationImportService.ProcessResult result
	) {
	}

	public record BatchResult(
		String nextAfterId,
		List<ProcessedComplex> processed,
		List<BatchFailure> failures
	) {
	}
}
