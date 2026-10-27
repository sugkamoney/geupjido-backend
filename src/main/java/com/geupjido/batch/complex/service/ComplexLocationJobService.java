package com.geupjido.batch.complex.service;

import java.util.concurrent.atomic.AtomicBoolean;

import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.stereotype.Service;

import com.geupjido.batch.complex.service.ComplexLocationBatchService.BatchResult;
import com.geupjido.batch.complex.service.ComplexLocationBatchService.ProcessedComplex;
import com.geupjido.batch.complex.service.ComplexLocationImportService.Kind;
import com.geupjido.batch.complex.service.ComplexZoneMatchService.Status;

/**
 * 단지 수집 뒤 미처리 단지 전체를 작은 구간으로 순차 처리한다.
 */
@Service
@ConditionalOnProperty(
	prefix = "app.external.geocoding",
	name = "enabled",
	havingValue = "true"
)
public class ComplexLocationJobService {

	private static final int BATCH_SIZE = 20;

	private final ComplexLocationBatchService batchService;
	private final AtomicBoolean running = new AtomicBoolean(false);

	public ComplexLocationJobService(ComplexLocationBatchService batchService) {
		this.batchService = batchService;
	}

	public JobResult processPending() {
		if (!running.compareAndSet(false, true)) {
			throw new IllegalStateException("단지 좌표·권역 작업이 이미 실행 중입니다.");
		}

		try {
			String cursor = "";
			int processedCount = 0;
			int matchedCount = 0;
			int unassignedCount = 0;
			int heldCount = 0;
			int skippedCount = 0;

			while (true) {
				BatchResult batch = batchService.processBatch(cursor, BATCH_SIZE);
				int attempted = batch.processed().size() + batch.failures().size();
				if (attempted == 0) {
					return new JobResult(
						processedCount, matchedCount, unassignedCount,
						heldCount, skippedCount, cursor
					);
				}
				if (batch.nextAfterId().compareTo(cursor) <= 0) {
					throw new IllegalStateException("단지 좌표·권역 작업 커서가 진행되지 않았습니다.");
				}

				processedCount += batch.processed().size();
				heldCount += batch.failures().size();
				for (ProcessedComplex item : batch.processed()) {
					Kind kind = item.result().kind();
					if (kind == Kind.ALREADY_ASSIGNED
						|| kind == Kind.SKIPPED_CONCURRENT_UPDATE) {
						skippedCount++;
						continue;
					}
					Status status = item.result().matchResult()
						.map(match -> match.status())
						.orElse(null);
					if (status == Status.MATCHED) {
						matchedCount++;
					} else {
						unassignedCount++;
					}
				}
				cursor = batch.nextAfterId();
			}
		} finally {
			running.set(false);
		}
	}

	public record JobResult(
		int processedCount,
		int matchedCount,
		int unassignedCount,
		int heldCount,
		int skippedCount,
		String lastCompletedId
	) {
	}
}
