package com.geupjido.match.api;

import java.net.URI;
import java.util.UUID;

import org.springframework.http.CacheControl;
import org.springframework.http.HttpHeaders;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.CookieValue;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RestController;

import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.media.Content;
import io.swagger.v3.oas.annotations.media.Schema;
import io.swagger.v3.oas.annotations.responses.ApiResponse;
import io.swagger.v3.oas.annotations.tags.Tag;

import com.geupjido.match.service.WorldcupGameNotFoundException;
import com.geupjido.match.service.WorldcupGameService;
import com.geupjido.match.service.WorldcupGameStartResult;
import com.geupjido.match.service.WorldcupPairRatioService;
import com.geupjido.user.api.AnonymousVisitorCookie;

@RestController
@RequestMapping(path = "/api/v1/worldcup", produces = MediaType.APPLICATION_JSON_VALUE)
@Tag(name = "월드컵 게임", description = "익명 쿠키로 게임을 시작하고 각 판의 선택 또는 건너뛰기를 저장합니다.")
public class WorldcupGameController {

	private static final URI CURRENT_GAME_URI = URI.create("/api/v1/worldcup/games/current");

	private final WorldcupGameService gameService;
	private final WorldcupPairRatioService pairRatioService;
	private final AnonymousVisitorCookie visitorCookie;

	public WorldcupGameController(
		WorldcupGameService gameService,
		WorldcupPairRatioService pairRatioService,
		AnonymousVisitorCookie visitorCookie
	) {
		this.gameService = gameService;
		this.pairRatioService = pairRatioService;
		this.visitorCookie = visitorCookie;
	}

	@PostMapping("/games")
	@Operation(summary = "배너에서 월드컵 게임 시작")
	@ApiResponse(responseCode = "201", description = "게임 생성")
	@ApiResponse(responseCode = "200", description = "진행 중인 게임 이어하기")
	@ApiResponse(
		responseCode = "429", description = "한국 날짜 기준 하루 6게임 시작 한도 도달",
		content = @Content(schema = @Schema(implementation = ApiErrorResponse.class))
	)
	@ApiResponse(
		responseCode = "409", description = "투표 가능한 대진 부족",
		content = @Content(schema = @Schema(implementation = ApiErrorResponse.class))
	)
	public ResponseEntity<WorldcupGameResponse> startFromBanner(
		@CookieValue(name = AnonymousVisitorCookie.NAME, required = false) String cookieValue
	) {
		AnonymousVisitorCookie.Visitor visitor = visitorCookie.identifyForStart(cookieValue);
		return started(gameService.startFromBanner(visitor.anonymousId()), visitor);
	}

	@PostMapping("/zones/{zoneId}/games")
	@Operation(summary = "선택한 권역에서 월드컵 게임 시작")
	@ApiResponse(responseCode = "201", description = "게임 생성")
	@ApiResponse(responseCode = "200", description = "진행 중인 게임 이어하기")
	@ApiResponse(
		responseCode = "429", description = "한국 날짜 기준 하루 6게임 시작 한도 도달",
		content = @Content(schema = @Schema(implementation = ApiErrorResponse.class))
	)
	@ApiResponse(
		responseCode = "409", description = "투표 가능한 대진 부족",
		content = @Content(schema = @Schema(implementation = ApiErrorResponse.class))
	)
	public ResponseEntity<WorldcupGameResponse> startForZone(
		@PathVariable String zoneId,
		@CookieValue(name = AnonymousVisitorCookie.NAME, required = false) String cookieValue
	) {
		AnonymousVisitorCookie.Visitor visitor = visitorCookie.identifyForStart(cookieValue);
		return started(gameService.startForZone(visitor.anonymousId(), zoneId), visitor);
	}

	@GetMapping("/games/current")
	@Operation(summary = "익명 쿠키로 최근 시작한 게임의 대진 재조회")
	@ApiResponse(responseCode = "200", description = "고정된 대진 조회")
	@ApiResponse(
		responseCode = "404", description = "조회할 게임 없음",
		content = @Content(schema = @Schema(implementation = ApiErrorResponse.class))
	)
	public ResponseEntity<WorldcupGameResponse> getCurrent(
		@CookieValue(name = AnonymousVisitorCookie.NAME, required = false) String cookieValue
	) {
		UUID anonymousId = visitorCookie.identify(cookieValue)
			.orElseThrow(WorldcupGameNotFoundException::new);
		return ResponseEntity.ok()
			.cacheControl(CacheControl.noStore())
			.body(WorldcupGameResponse.from(gameService.findCurrent(anonymousId)));
	}

	@PutMapping(path = "/games/current/rounds/{roundNumber}/vote",
		consumes = MediaType.APPLICATION_JSON_VALUE)
	@Operation(summary = "현재 게임의 다음 판 선택 확정")
	@ApiResponse(responseCode = "200", description = "선택 저장 또는 동일 선택 재조회")
	@ApiResponse(
		responseCode = "400", description = "판 번호 또는 후보 단지 오류",
		content = @Content(schema = @Schema(implementation = ApiErrorResponse.class))
	)
	@ApiResponse(
		responseCode = "404", description = "조회할 게임 없음",
		content = @Content(schema = @Schema(implementation = ApiErrorResponse.class))
	)
	@ApiResponse(
		responseCode = "409", description = "선택 변경 또는 진행 순서 오류",
		content = @Content(schema = @Schema(implementation = ApiErrorResponse.class))
	)
	public ResponseEntity<WorldcupGameResponse> select(
		@PathVariable int roundNumber,
		@RequestBody(required = false) WorldcupVoteRequest request,
		@CookieValue(name = AnonymousVisitorCookie.NAME, required = false) String cookieValue
	) {
		UUID anonymousId = visitorCookie.identify(cookieValue)
			.orElseThrow(WorldcupGameNotFoundException::new);
		String selectedComplexId = request == null ? null : request.selectedComplexId();
		var state = gameService.select(anonymousId, roundNumber, selectedComplexId);
		return ResponseEntity.ok()
			.cacheControl(CacheControl.noStore())
			.body(WorldcupGameResponse.from(state, WorldcupGameResponse.RoundResult.from(
				roundNumber, pairRatioService.forRound(state, roundNumber))));
	}

	@PutMapping("/games/current/rounds/{roundNumber}/skip")
	@Operation(summary = "현재 게임의 다음 판 건너뛰기 확정")
	@ApiResponse(responseCode = "200", description = "건너뛰기 저장 또는 동일 요청 재조회")
	@ApiResponse(responseCode = "400", description = "판 번호 오류",
		content = @Content(schema = @Schema(implementation = ApiErrorResponse.class)))
	@ApiResponse(responseCode = "404", description = "조회할 게임 없음",
		content = @Content(schema = @Schema(implementation = ApiErrorResponse.class)))
	@ApiResponse(responseCode = "409", description = "이미 선택했거나 진행 순서 오류",
		content = @Content(schema = @Schema(implementation = ApiErrorResponse.class)))
	public ResponseEntity<WorldcupGameResponse> skip(
		@PathVariable int roundNumber,
		@CookieValue(name = AnonymousVisitorCookie.NAME, required = false) String cookieValue
	) {
		UUID anonymousId = visitorCookie.identify(cookieValue)
			.orElseThrow(WorldcupGameNotFoundException::new);
		var state = gameService.skip(anonymousId, roundNumber);
		return ResponseEntity.ok()
			.cacheControl(CacheControl.noStore())
			.body(WorldcupGameResponse.from(state, WorldcupGameResponse.RoundResult.from(
				roundNumber, pairRatioService.forRound(state, roundNumber))));
	}

	private ResponseEntity<WorldcupGameResponse> started(
		WorldcupGameStartResult result,
		AnonymousVisitorCookie.Visitor visitor
	) {
		ResponseEntity.BodyBuilder response = (result.created()
			? ResponseEntity.created(CURRENT_GAME_URI) : ResponseEntity.ok())
			.cacheControl(CacheControl.noStore());
		if (visitor.setCookie() != null) {
			response.header(HttpHeaders.SET_COOKIE, visitor.setCookie());
		}
		return response.body(WorldcupGameResponse.from(result.state()));
	}
}
