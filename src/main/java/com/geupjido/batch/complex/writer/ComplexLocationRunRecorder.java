package com.geupjido.batch.complex.writer;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.StandardOpenOption;
import java.time.Instant;
import java.util.List;

import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.stereotype.Component;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.geupjido.batch.complex.service.ComplexLocationBatchService.FailureKind;
import com.geupjido.batch.complex.service.ComplexLocationImportService.ProcessResult;
import com.geupjido.zone.domain.Zone;

/**
 * 수동 좌표 배치의 단지별 결과를 로컬 JSONL 매니페스트에 즉시 기록한다.
 */
@Component
@ConditionalOnProperty(
	prefix = "app.external.geocoding",
	name = "enabled",
	havingValue = "true"
)
public class ComplexLocationRunRecorder {

	private final ObjectMapper objectMapper;
	private final Path manifestPath;

	public ComplexLocationRunRecorder(
		ObjectMapper objectMapper,
		@Value("${app.complex-location-import.batch-manifest-path:data/complex/location-runs/local.jsonl}")
		String manifestPath
	) {
		if (manifestPath == null || manifestPath.isBlank()) {
			throw new IllegalArgumentException("배치 매니페스트 경로가 필요합니다.");
		}
		this.objectMapper = objectMapper;
		this.manifestPath = Path.of(manifestPath);
	}

	public void prepare() {
		try {
			Files.createDirectories(manifestPath.toAbsolutePath().getParent());
			Files.writeString(
				manifestPath,
				"",
				StandardCharsets.UTF_8,
				StandardOpenOption.CREATE,
				StandardOpenOption.APPEND
			);
		} catch (IOException ex) {
			throw new IllegalStateException(
				"단지 좌표 배치 결과 파일을 준비하지 못했습니다: " + manifestPath,
				ex
			);
		}
	}

	public void recordProcessed(String complexId, ProcessResult result) {
		append(new Entry(
			Instant.now().toString(),
			complexId,
			result.kind().name(),
			result.matchResult().map(match -> match.status().name()).orElse(null),
			result.matchResult()
				.map(match -> match.candidates().stream().map(Zone::getId).toList())
				.orElseGet(List::of),
			complexId
		));
	}

	public void recordFailure(String complexId, FailureKind kind) {
		append(new Entry(
			Instant.now().toString(), complexId, kind.name(),
			null, List.of(), complexId
		));
	}

	public void recordAborted(String complexId, String lastCompletedId) {
		append(new Entry(
			Instant.now().toString(), complexId, "ABORTED",
			null, List.of(), lastCompletedId
		));
	}

	private synchronized void append(Entry entry) {
		try {
			Files.createDirectories(manifestPath.toAbsolutePath().getParent());
			Files.writeString(
				manifestPath,
				objectMapper.writeValueAsString(entry) + "\n",
				StandardCharsets.UTF_8,
				StandardOpenOption.CREATE,
				StandardOpenOption.APPEND
			);
		} catch (IOException ex) {
			throw new IllegalStateException(
				"단지 좌표 배치 결과를 기록하지 못했습니다: " + manifestPath,
				ex
			);
		}
	}

	private record Entry(
		String recordedAt,
		String complexId,
		String resultKind,
		String zoneStatus,
		List<String> candidateZoneIds,
		String nextAfterId
	) {
	}
}
