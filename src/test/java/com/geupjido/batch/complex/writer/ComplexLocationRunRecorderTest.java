package com.geupjido.batch.complex.writer;

import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;
import java.util.Optional;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.geupjido.batch.complex.service.ComplexLocationBatchService.FailureKind;
import com.geupjido.batch.complex.service.ComplexLocationImportService.Kind;
import com.geupjido.batch.complex.service.ComplexLocationImportService.ProcessResult;
import com.geupjido.batch.complex.service.ComplexZoneMatchService.MatchResult;
import com.geupjido.batch.complex.service.ComplexZoneMatchService.Status;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

class ComplexLocationRunRecorderTest {

	@TempDir
	Path tempDir;

	private final ObjectMapper objectMapper = new ObjectMapper();

	@Test
	void 단지별_결과와_보류_및_중단_커서를_JSONL에_즉시_기록한다() throws Exception {
		Path manifest = tempDir.resolve("runs/local.jsonl");
		var recorder = new ComplexLocationRunRecorder(
			objectMapper, manifest.toString()
		);
		String firstId = "A10000001";
		String secondId = "A10000002";
		String thirdId = "A10000003";

		recorder.recordProcessed(firstId, new ProcessResult(
			Kind.NEW_LOCATION_SAVED,
			Optional.of(new MatchResult(Status.OUTSIDE, List.of()))
		));
		recorder.recordFailure(secondId, FailureKind.MULTIPLE_GEOCODING_RESULTS);
		recorder.recordAborted(thirdId, secondId);

		List<String> lines = Files.readAllLines(manifest, StandardCharsets.UTF_8);
		assertThat(lines).hasSize(3);

		var first = objectMapper.readTree(lines.get(0));
		assertThat(first.path("complexId").asText()).isEqualTo(firstId);
		assertThat(first.path("resultKind").asText())
			.isEqualTo("NEW_LOCATION_SAVED");
		assertThat(first.path("zoneStatus").asText()).isEqualTo("OUTSIDE");
		assertThat(first.path("candidateZoneIds").isEmpty()).isTrue();
		assertThat(first.path("nextAfterId").asText()).isEqualTo(firstId);
		assertThat(first.path("recordedAt").asText()).isNotBlank();

		var held = objectMapper.readTree(lines.get(1));
		assertThat(held.path("complexId").asText()).isEqualTo(secondId);
		assertThat(held.path("resultKind").asText())
			.isEqualTo("MULTIPLE_GEOCODING_RESULTS");
		assertThat(held.path("nextAfterId").asText()).isEqualTo(secondId);

		var aborted = objectMapper.readTree(lines.get(2));
		assertThat(aborted.path("complexId").asText()).isEqualTo(thirdId);
		assertThat(aborted.path("resultKind").asText()).isEqualTo("ABORTED");
		assertThat(aborted.path("nextAfterId").asText()).isEqualTo(secondId);
		assertThat(aborted.has("address")).isFalse();
		assertThat(aborted.has("apiResponse")).isFalse();
	}

	@Test
	void 결과_파일을_쓸_수_없으면_조용히_진행하지_않는다() throws Exception {
		Path directory = tempDir.resolve("manifest.jsonl");
		Files.createDirectories(directory);
		var recorder = new ComplexLocationRunRecorder(
			objectMapper, directory.toString()
		);

		assertThatThrownBy(recorder::prepare)
			.isInstanceOf(IllegalStateException.class)
			.hasMessageContaining("준비하지 못했습니다");
	}
}
