package com.geupjido.match.api;

import java.util.List;

import org.springframework.http.CacheControl;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;

import com.geupjido.match.service.WorldcupZoneSelectionService;

@RestController
@RequestMapping(path = "/api/v1/worldcup/zones", produces = MediaType.APPLICATION_JSON_VALUE)
@Tag(name = "월드컵 권역 선택", description = "권역 목록과 대체 인접 권역을 조회합니다.")
public class WorldcupZoneSelectionController {

	private final WorldcupZoneSelectionService service;

	public WorldcupZoneSelectionController(WorldcupZoneSelectionService service) {
		this.service = service;
	}

	@GetMapping
	@Operation(summary = "월드컵 권역 선택 목록 조회")
	public ResponseEntity<List<WorldcupZoneSelectionResponse>> list() {
		return ResponseEntity.ok().cacheControl(CacheControl.noStore())
			.body(service.list().stream().map(WorldcupZoneSelectionResponse::from).toList());
	}

	@GetMapping("/{zoneId}/alternatives")
	@Operation(summary = "시작할 수 없는 권역의 인접 대체 권역 조회")
	public ResponseEntity<List<WorldcupZoneSelectionResponse>> alternatives(
		@PathVariable String zoneId
	) {
		return ResponseEntity.ok().cacheControl(CacheControl.noStore())
			.body(service.alternatives(zoneId).stream()
				.map(WorldcupZoneSelectionResponse::from).toList());
	}
}
