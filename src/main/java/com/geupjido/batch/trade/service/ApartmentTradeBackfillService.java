package com.geupjido.batch.trade.service;

import java.io.BufferedWriter;
import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.StandardOpenOption;
import java.nio.file.StandardCopyOption;
import java.time.YearMonth;
import java.time.format.DateTimeFormatter;
import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Set;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.stereotype.Service;

import com.geupjido.batch.location.reader.LocationMappingReader;
import com.geupjido.batch.trade.model.ApartmentTradeBackfillResult;
import com.geupjido.batch.trade.model.ApartmentTradeImportResult;
import com.geupjido.batch.trade.model.ApartmentTradePartialImportResult;
import com.geupjido.batch.trade.exception.ApartmentTradeApiException;
import com.geupjido.batch.trade.exception.ApartmentTradeMappingException;

/** 권역 매핑의 시군구를 월별로 순회하며 공공 API 거래 원본을 소급 수집한다. */
@Service
@ConditionalOnProperty(
	prefix = "app.external.apartment-trade",
	name = "enabled",
	havingValue = "true"
)
public class ApartmentTradeBackfillService {

	private static final Logger log =
		LoggerFactory.getLogger(ApartmentTradeBackfillService.class);
	private static final DateTimeFormatter MONTH_FORMAT =
		DateTimeFormatter.ofPattern("yyyyMM");
	private static final String HEADER =
		"계약월\t시군구코드\t상태\t원본\t신규\t건너뛰기\t갱신\t실패사유\n";
	private static final String LEGACY_HEADER =
		"계약월\t시군구코드\t상태\t원본\t신규\t건너뛰기\t갱신";

	private final LocationMappingReader mappingReader;
	private final ApartmentTradeImportService importService;

	public ApartmentTradeBackfillService(
		LocationMappingReader mappingReader,
		ApartmentTradeImportService importService
	) {
		this.mappingReader = mappingReader;
		this.importService = importService;
	}

	public ApartmentTradeBackfillResult importRange(
		Path mappingPath,
		YearMonth fromMonth,
		YearMonth throughMonth,
		int maxPairs,
		Path manifestPath
	) throws IOException {
		if (mappingPath == null || manifestPath == null
			|| fromMonth == null || throughMonth == null
			|| fromMonth.isAfter(throughMonth) || maxPairs <= 0) {
			throw new IllegalArgumentException(
				"매핑 경로, 결과 경로, 올바른 계약월 범위와 양수 처리 건수가 필요합니다."
			);
		}
		List<String> districtCodes = mappingReader.read(mappingPath)
			.zones().stream().map(zone -> zone.cityId())
			.distinct().sorted().toList();
		if (districtCodes.isEmpty()) {
			throw new IllegalStateException("권역 매핑에 수집할 시군구가 없습니다.");
		}
		List<Pair> pairs = new ArrayList<>();
		for (YearMonth month = fromMonth; !month.isAfter(throughMonth);
			month = month.plusMonths(1)) {
			for (String districtCode : districtCodes) {
				pairs.add(new Pair(month.format(MONTH_FORMAT), districtCode));
			}
		}
		Set<Pair> completed = readCompleted(manifestPath);
		Path parent = manifestPath.toAbsolutePath().getParent();
		Files.createDirectories(parent);
		upgradeLegacyManifest(manifestPath);
		boolean writeHeader = !Files.exists(manifestPath)
			|| Files.size(manifestPath) == 0;
		int alreadyCompleted = 0;
		int processed = 0;
		int failed = 0;
		long source = 0;
		long created = 0;
		long skipped = 0;
		long updated = 0;
		long conflicts = 0;
		try (BufferedWriter writer = Files.newBufferedWriter(
			manifestPath, StandardOpenOption.CREATE,
			StandardOpenOption.APPEND
		)) {
			if (writeHeader) {
				writer.write(HEADER);
				writer.flush();
			}
			for (Pair pair : pairs) {
				if (completed.contains(pair)) {
					alreadyCompleted++;
					continue;
				}
				if (processed >= maxPairs) {
					break;
				}
				log.info("실거래가 소급 적재 시작. 계약월={}, 시군구={}",
					pair.month(), pair.districtCode());
				processed++;
				try {
					ApartmentTradePartialImportResult partial =
						importService.importBackfillMonth(
						pair.districtCode(), pair.month()
						);
					ApartmentTradeImportResult result = partial.imported();
					String status = partial.conflictRecords() == 0
						? "SUCCESS" : "PARTIAL";
					writeResult(writer, pair, status, result,
						partial.conflictRecords(), partial.conflictRecords() == 0 ? ""
							: "SOURCE_CONFLICT:" + partial.conflictRecords());
					source += result.totalCount() + partial.conflictRecords();
					created += result.createdCount();
					skipped += result.skippedCount();
					updated += result.updatedCount();
					conflicts += partial.conflictRecords();
					log.info("실거래가 소급 적재 완료. 계약월={}, 시군구={}, 신규={}, 보류={}",
						pair.month(), pair.districtCode(), result.createdCount(),
						partial.conflictRecords());
				} catch (RuntimeException exception) {
					String reason = failureReason(exception);
					writeResult(writer, pair, "FAILED", null, 0, reason);
					failed++;
					log.warn("실거래가 소급 적재 실패. 계약월={}, 시군구={}, 사유={}",
						pair.month(), pair.districtCode(), reason);
				}
			}
		}
		return new ApartmentTradeBackfillResult(
			pairs.size(), alreadyCompleted, processed, failed,
			source, created, skipped, updated, conflicts
		);
	}

	private static Set<Pair> readCompleted(Path path) throws IOException {
		Set<Pair> completed = new HashSet<>();
		if (!Files.exists(path)) {
			return completed;
		}
		List<String> lines = Files.readAllLines(path);
		if (!lines.isEmpty()
			&& !HEADER.stripTrailing().equals(lines.getFirst())
			&& !LEGACY_HEADER.equals(lines.getFirst())) {
			throw new IllegalStateException(
				"실거래가 소급 적재 결과 파일의 헤더가 다릅니다: " + path
			);
		}
		for (int index = 1; index < lines.size(); index++) {
			String[] fields = lines.get(index).split("\t", -1);
			if ((fields.length != 7 && fields.length != 8)
				|| !("SUCCESS".equals(fields[2])
					|| "FAILED".equals(fields[2])
					|| "PARTIAL".equals(fields[2]))) {
				throw new IllegalStateException(
					"실거래가 소급 적재 결과 파일 형식이 올바르지 않습니다: "
						+ (index + 1) + "행"
				);
			}
			if ("SUCCESS".equals(fields[2])
				|| "PARTIAL".equals(fields[2])) {
				completed.add(new Pair(fields[0], fields[1]));
			}
		}
		return completed;
	}

	private static void upgradeLegacyManifest(Path path) throws IOException {
		if (!Files.exists(path)) {
			return;
		}
		List<String> lines = Files.readAllLines(path);
		if (lines.isEmpty() || !LEGACY_HEADER.equals(lines.getFirst())) {
			return;
		}
		Path temporary = Files.createTempFile(
			path.toAbsolutePath().getParent(), "trade-manifest-", ".tmp"
		);
		try {
			try (BufferedWriter writer = Files.newBufferedWriter(temporary)) {
				writer.write(HEADER);
				for (int index = 1; index < lines.size(); index++) {
					writer.write(lines.get(index));
					if (lines.get(index).split("\t", -1).length == 7) {
						writer.write('\t');
					}
					writer.write('\n');
				}
			}
			Files.move(temporary, path, StandardCopyOption.REPLACE_EXISTING);
		} finally {
			Files.deleteIfExists(temporary);
		}
	}

	private static String failureReason(RuntimeException exception) {
		if (exception instanceof ApartmentTradeApiException) {
			return "API_FAILURE";
		}
		if (exception instanceof ApartmentTradeMappingException) {
			return "MAPPING_FAILURE";
		}
		if ("해제된 거래가 정상 거래로 다시 제공되었습니다."
			.equals(exception.getMessage())) {
			return "CANCELED_TO_NORMAL";
		}
		if ("이미 해제된 거래의 해제일과 새 해제일이 다릅니다."
			.equals(exception.getMessage())) {
			return "CANCELLATION_DATE_MISMATCH";
		}
		return "OTHER_FAILURE";
	}

	private static void writeResult(
		BufferedWriter writer,
		Pair pair,
		String status,
		ApartmentTradeImportResult result,
		int conflicts,
		String reason
	) throws IOException {
		writer.write(pair.month() + "\t" + pair.districtCode() + "\t" + status);
		if (result == null) {
			writer.write("\t\t\t\t\t" + reason + "\n");
		} else {
			writer.write("\t" + (result.totalCount() + conflicts) + "\t"
				+ result.createdCount() + "\t" + result.skippedCount()
				+ "\t" + result.updatedCount() + "\t" + reason + "\n");
		}
		writer.flush();
	}

	private record Pair(String month, String districtCode) {
	}
}
