package com.geupjido.batch.trade.service;

import java.nio.file.Path;
import java.time.Instant;
import java.time.YearMonth;
import java.util.ArrayList;
import java.util.List;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.stereotype.Service;

import com.geupjido.batch.trade.exception.ApartmentTradeApiException;
import com.geupjido.batch.trade.exception.ApartmentTradeMappingException;
import com.geupjido.batch.trade.model.ApartmentTradeImportResult;
import com.geupjido.batch.trade.model.ApartmentTradePartialImportResult;
import com.geupjido.batch.trade.model.WeeklyTradeCollectionResult;
import com.geupjido.batch.trade.model.WeeklyTradeCollectionResult.PairResult;
import com.geupjido.batch.trade.model.WeeklyTradeCollectionResult.Status;
import com.geupjido.batch.trade.service.WeeklyTradeTargetPlanner.Target;
import com.geupjido.batch.trade.writer.WeeklyTradeRunRecorder;

/** 최근 계약월의 거래를 시군구별로 수집하고 처리 상태를 구분한다. */
@Service
@ConditionalOnProperty(
	prefix = "app.external.apartment-trade",
	name = "enabled",
	havingValue = "true"
)
public class WeeklyTradeCollectionService {

	private static final Logger log =
		LoggerFactory.getLogger(WeeklyTradeCollectionService.class);

	private final WeeklyTradeTargetPlanner targetPlanner;
	private final ApartmentTradeImportService importService;
	private final WeeklyTradeRunRecorder recorder;

	public WeeklyTradeCollectionService(
		WeeklyTradeTargetPlanner targetPlanner,
		ApartmentTradeImportService importService,
		WeeklyTradeRunRecorder recorder
	) {
		this.targetPlanner = targetPlanner;
		this.importService = importService;
		this.recorder = recorder;
	}

	public WeeklyTradeCollectionResult collect(
		Path mappingPath, YearMonth currentMonth, int monthsToCollect
	) {
		return collectTargets(targetPlanner.plan(
			mappingPath, currentMonth, monthsToCollect
		));
	}

	public WeeklyTradeCollectionResult collectOne(
		Path mappingPath, String districtCode, YearMonth dealMonth
	) {
		Target target = targetPlanner.plan(mappingPath, dealMonth, 1).stream()
			.filter(candidate -> candidate.districtCode().equals(districtCode))
			.findFirst()
			.orElseThrow(() -> new IllegalArgumentException(
				"권역 매핑에 없는 시군구 코드입니다: " + districtCode
			));
		return collectTargets(List.of(target));
	}

	private WeeklyTradeCollectionResult collectTargets(List<Target> targets) {
		Instant startedAt = recorder.begin();
		List<PairResult> results = new ArrayList<>(targets.size());
		for (Target target : targets) {
			try {
				ApartmentTradePartialImportResult partial =
					importService.importBackfillMonth(
						target.districtCode(), target.dealYearMonth()
					);
				ApartmentTradeImportResult imported = partial.imported();
				Status status = partial.conflictRecords() == 0
					? Status.SUCCESS : Status.PARTIAL;
				PairResult result = new PairResult(
					target.districtCode(), target.dealYearMonth(), status,
					imported.totalCount() + partial.conflictRecords(),
					imported.createdCount(), imported.skippedCount(),
					imported.updatedCount(), partial.conflictRecords(),
					partial.conflictRecords() == 0 ? null
						: "SOURCE_CONFLICT:" + partial.conflictRecords(),
					partial.newConflictRecords()
				);
				recorder.record(startedAt, result);
				results.add(result);
				log.info("실거래가 주간 수집 결과. 시군구={}, 계약월={}, 상태={}, "
					+ "신규={}, 갱신={}, 충돌={}, 처음 확인한 충돌={}", target.districtCode(),
					target.dealYearMonth(), status, imported.createdCount(),
					imported.updatedCount(), partial.conflictRecords(),
					partial.newConflictRecords());
			} catch (ApartmentTradeApiException | ApartmentTradeMappingException exception) {
				String reason = exception instanceof ApartmentTradeApiException
					? "API_FAILURE" : "MAPPING_FAILURE";
				PairResult result = new PairResult(
					target.districtCode(), target.dealYearMonth(), Status.FAILED,
					0, 0, 0, 0, 0, reason
				);
				recorder.record(startedAt, result);
				results.add(result);
				log.warn("실거래가 주간 수집 실패. 시군구={}, 계약월={}, 사유={}",
					target.districtCode(), target.dealYearMonth(), reason,
					exception);
			}
		}
		return new WeeklyTradeCollectionResult(results);
	}
}
