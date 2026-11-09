package com.geupjido.match.api;

import org.springframework.http.CacheControl;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;

import com.geupjido.match.service.WorldcupZoneRankingService;

@RestController
@RequestMapping(path = "/api/v1/worldcup", produces = MediaType.APPLICATION_JSON_VALUE)
@Tag(name = "월드컵 급지·순위", description = "권역의 현재 급지와 단지 순위를 조회합니다.")
public class WorldcupZoneRankingController {

	private final WorldcupZoneRankingService service;

	public WorldcupZoneRankingController(WorldcupZoneRankingService service) {
		this.service = service;
	}

	@GetMapping("/zones/{zoneId}/ranking")
	@Operation(summary = "권역 급지와 단지 순위 조회")
	public ResponseEntity<WorldcupZoneRankingResponse> get(@PathVariable String zoneId) {
		return ResponseEntity.ok().cacheControl(CacheControl.noStore())
			.body(WorldcupZoneRankingResponse.from(service.get(zoneId)));
	}
}
