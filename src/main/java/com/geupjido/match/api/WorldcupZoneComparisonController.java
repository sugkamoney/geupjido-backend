package com.geupjido.match.api;

import java.util.UUID;

import org.springframework.http.CacheControl;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.CookieValue;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;

import com.geupjido.match.service.WorldcupGameNotFoundException;
import com.geupjido.match.service.WorldcupGameService;
import com.geupjido.match.service.WorldcupPairRatioService;
import com.geupjido.user.api.AnonymousVisitorCookie;

@RestController
@RequestMapping(path = "/api/v1/worldcup/zones/{zoneId}/games/current",
	produces = MediaType.APPLICATION_JSON_VALUE)
@Tag(name = "월드컵")
public class WorldcupZoneComparisonController {

	private final WorldcupGameService gameService;
	private final WorldcupPairRatioService pairRatioService;
	private final AnonymousVisitorCookie visitorCookie;

	public WorldcupZoneComparisonController(WorldcupGameService gameService,
		WorldcupPairRatioService pairRatioService, AnonymousVisitorCookie visitorCookie) {
		this.gameService = gameService;
		this.pairRatioService = pairRatioService;
		this.visitorCookie = visitorCookie;
	}

	@GetMapping
	@Operation(summary = "선택한 권역 안 단지 비교 이어하기")
	public ResponseEntity<WorldcupGameResponse> getCurrent(@PathVariable String zoneId,
		@CookieValue(name = AnonymousVisitorCookie.NAME, required = false) String cookieValue) {
		return ResponseEntity.ok().cacheControl(CacheControl.noStore())
			.body(WorldcupGameResponse.from(gameService.findZoneCurrent(visitorId(cookieValue), zoneId)));
	}

	@PutMapping(path = "/rounds/{roundNumber}/vote", consumes = MediaType.APPLICATION_JSON_VALUE)
	@Operation(summary = "권역 안 단지 비교의 다음 판 선택")
	public ResponseEntity<WorldcupGameResponse> select(@PathVariable String zoneId,
		@PathVariable int roundNumber, @RequestBody(required = false) WorldcupVoteRequest request,
		@CookieValue(name = AnonymousVisitorCookie.NAME, required = false) String cookieValue) {
		String selectedComplexId = request == null ? null : request.selectedComplexId();
		var state = gameService.selectZone(visitorId(cookieValue), zoneId,
			roundNumber, selectedComplexId);
		return ResponseEntity.ok().cacheControl(CacheControl.noStore())
			.body(WorldcupGameResponse.from(state, WorldcupGameResponse.RoundResult.from(
				roundNumber, pairRatioService.forRound(state, roundNumber))));
	}

	@PutMapping("/rounds/{roundNumber}/skip")
	@Operation(summary = "권역 안 단지 비교의 다음 판 건너뛰기")
	public ResponseEntity<WorldcupGameResponse> skip(@PathVariable String zoneId,
		@PathVariable int roundNumber,
		@CookieValue(name = AnonymousVisitorCookie.NAME, required = false) String cookieValue) {
		var state = gameService.skipZone(visitorId(cookieValue), zoneId, roundNumber);
		return ResponseEntity.ok().cacheControl(CacheControl.noStore())
			.body(WorldcupGameResponse.from(state, WorldcupGameResponse.RoundResult.from(
				roundNumber, pairRatioService.forRound(state, roundNumber))));
	}

	private UUID visitorId(String cookieValue) {
		return visitorCookie.identify(cookieValue)
			.orElseThrow(WorldcupGameNotFoundException::new);
	}
}
