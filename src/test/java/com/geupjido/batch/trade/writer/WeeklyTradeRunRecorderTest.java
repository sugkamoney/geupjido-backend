package com.geupjido.batch.trade.writer;

import java.nio.file.Files;
import java.nio.file.Path;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import com.geupjido.batch.trade.config.WeeklyTradeRefreshProperties;
import com.geupjido.batch.trade.model.WeeklyTradeCollectionResult.PairResult;
import com.geupjido.batch.trade.model.WeeklyTradeCollectionResult.Status;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

class WeeklyTradeRunRecorderTest {

	@TempDir
	Path temporaryDirectory;

	@Test
	void 같은_시군구와_계약월도_실행할_때마다_결과를_추가한다() throws Exception {
		Path report = temporaryDirectory.resolve("runs/weekly.tsv");
		WeeklyTradeRunRecorder recorder = recorder(report);
		var first = recorder.begin();
		recorder.record(first, result(Status.FAILED, "API_FAILURE"));
		var second = recorder.begin();
		recorder.record(second, result(Status.SUCCESS, null));

		assertThat(Files.readAllLines(report)).hasSize(3);
		assertThat(Files.readAllLines(report).get(1))
			.contains("\t11110\t202611\tFAILED\t")
			.endsWith("\tAPI_FAILURE\t0");
		assertThat(Files.readAllLines(report).get(2))
			.contains("\t11110\t202611\tSUCCESS\t");
		assertThat(Files.readAllLines(report).getFirst())
			.endsWith("\t신규충돌");
	}

	@Test
	void 기존_결과_행을_보존하고_신규충돌_열을_추가한다() throws Exception {
		Path report = temporaryDirectory.resolve("weekly.tsv");
		String oldHeader = "실행시각\t시군구코드\t계약월\t상태\t원본\t신규"
			+ "\t건너뛰기\t갱신\t충돌\t실패사유";
		String oldRow = "2026-11-01T00:00:00Z\t11140\t202609\tPARTIAL"
			+ "\t4\t0\t0\t0\t4\tSOURCE_CONFLICT:4";
		Files.writeString(report, oldHeader + "\n" + oldRow + "\n");

		WeeklyTradeRunRecorder recorder = recorder(report);
		var startedAt = recorder.begin();
		recorder.record(startedAt, new PairResult(
			"11140", "202609", Status.PARTIAL,
			4, 0, 0, 0, 4, "SOURCE_CONFLICT:4", 1));

		assertThat(Files.readAllLines(report)).hasSize(3);
		assertThat(Files.readAllLines(report).getFirst())
			.isEqualTo(oldHeader + "\t신규충돌");
		assertThat(Files.readAllLines(report).get(1))
			.isEqualTo(oldRow + "\t");
		assertThat(Files.readAllLines(report).get(2)).endsWith("\t1");
	}

	@Test
	void 기존_결과_파일_형식이_다르면_실행_전에_중단한다() throws Exception {
		Path report = temporaryDirectory.resolve("weekly.tsv");
		Files.writeString(report, "잘못된 헤더\n");

		assertThatThrownBy(() -> recorder(report).begin())
			.isInstanceOf(IllegalStateException.class)
			.hasMessageContaining("헤더가 다릅니다");
	}

	private static WeeklyTradeRunRecorder recorder(Path report) {
		return new WeeklyTradeRunRecorder(new WeeklyTradeRefreshProperties(
			false, false, false, null, null,
			Path.of("mapping.json"), Path.of("dong.tsv"), 3,
			report
		));
	}

	private static PairResult result(Status status, String reason) {
		return new PairResult("11110", "202611", status,
			1, 1, 0, 0, 0, reason);
	}
}
