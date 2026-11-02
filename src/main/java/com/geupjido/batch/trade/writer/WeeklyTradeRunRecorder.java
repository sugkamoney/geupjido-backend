package com.geupjido.batch.trade.writer;

import java.io.BufferedReader;
import java.io.IOException;
import java.io.UncheckedIOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.AtomicMoveNotSupportedException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.StandardCopyOption;
import java.nio.file.StandardOpenOption;
import java.time.Instant;
import java.util.List;

import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.stereotype.Component;

import com.geupjido.batch.trade.config.WeeklyTradeRefreshProperties;
import com.geupjido.batch.trade.model.WeeklyTradeCollectionResult.PairResult;

/** 주간 수집의 시군구·계약월별 결과를 Git 제외 파일에 즉시 추가한다. */
@Component
@ConditionalOnProperty(
	prefix = "app.external.apartment-trade",
	name = "enabled",
	havingValue = "true"
)
public class WeeklyTradeRunRecorder {

	private static final String LEGACY_HEADER =
		"실행시각\t시군구코드\t계약월\t상태\t원본\t신규\t건너뛰기\t갱신\t충돌\t실패사유";
	private static final String HEADER = LEGACY_HEADER + "\t신규충돌";

	private final Path path;

	public WeeklyTradeRunRecorder(WeeklyTradeRefreshProperties properties) {
		this.path = properties.reportPath();
	}

	public synchronized Instant begin() {
		if (path == null) {
			throw new IllegalStateException("주간 실거래가 결과 파일 경로가 필요합니다.");
		}
		try {
			Files.createDirectories(path.toAbsolutePath().getParent());
			if (Files.exists(path) && Files.size(path) > 0) {
				try (BufferedReader reader = Files.newBufferedReader(
					path, StandardCharsets.UTF_8
				)) {
					String existingHeader = reader.readLine();
					if (LEGACY_HEADER.equals(existingHeader)) {
						migrateLegacyReport();
					} else if (!HEADER.equals(existingHeader)) {
						throw new IllegalStateException(
							"주간 실거래가 결과 파일의 헤더가 다릅니다: " + path
						);
					}
				}
			} else {
				Files.writeString(path, HEADER + "\n", StandardCharsets.UTF_8,
					StandardOpenOption.CREATE, StandardOpenOption.APPEND);
			}
			return Instant.now();
		} catch (IOException exception) {
			throw new UncheckedIOException(
				"주간 실거래가 결과 파일을 준비할 수 없습니다: " + path,
				exception
			);
		}
	}

	private void migrateLegacyReport() throws IOException {
		List<String> lines = Files.readAllLines(path, StandardCharsets.UTF_8);
		Path temporary = Files.createTempFile(
			path.toAbsolutePath().getParent(), "weekly-trade-", ".tsv"
		);
		try {
			StringBuilder migrated = new StringBuilder(HEADER).append('\n');
			for (int index = 1; index < lines.size(); index++) {
				migrated.append(lines.get(index)).append('\t').append('\n');
			}
			Files.writeString(temporary, migrated, StandardCharsets.UTF_8);
			try {
				Files.move(temporary, path, StandardCopyOption.REPLACE_EXISTING,
					StandardCopyOption.ATOMIC_MOVE);
			} catch (AtomicMoveNotSupportedException exception) {
				Files.move(temporary, path, StandardCopyOption.REPLACE_EXISTING);
			}
		} finally {
			Files.deleteIfExists(temporary);
		}
	}

	public synchronized void record(Instant startedAt, PairResult result) {
		String line = startedAt + "\t" + result.districtCode() + "\t"
			+ result.dealYearMonth() + "\t" + result.status() + "\t"
			+ result.sourceTrades() + "\t" + result.createdTrades() + "\t"
			+ result.skippedTrades() + "\t" + result.updatedTrades()
			+ "\t" + result.conflictTrades() + "\t"
			+ (result.failureReason() == null ? "" : result.failureReason())
			+ "\t" + result.newConflictTrades()
			+ "\n";
		try {
			Files.writeString(path, line, StandardCharsets.UTF_8,
				StandardOpenOption.CREATE, StandardOpenOption.APPEND);
		} catch (IOException exception) {
			throw new UncheckedIOException(
				"주간 실거래가 결과를 기록할 수 없습니다: " + path,
				exception
			);
		}
	}
}
