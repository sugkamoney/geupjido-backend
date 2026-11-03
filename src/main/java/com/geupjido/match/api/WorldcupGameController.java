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
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.media.Content;
import io.swagger.v3.oas.annotations.media.Schema;
import io.swagger.v3.oas.annotations.responses.ApiResponse;
import io.swagger.v3.oas.annotations.tags.Tag;

import com.geupjido.match.model.GeneratedMatchGame;
import com.geupjido.match.service.WorldcupGameNotFoundException;
import com.geupjido.match.service.WorldcupGameService;
import com.geupjido.user.api.AnonymousVisitorCookie;

@RestController
@RequestMapping(path = "/api/v1/worldcup", produces = MediaType.APPLICATION_JSON_VALUE)
@Tag(name = "월드컵 게임", description = "익명 쿠키로 게임을 시작하고 고정된 대진을 다시 조회합니다.")
public class WorldcupGameController {

	private static final URI CURRENT_GAME_URI = URI.create("/api/v1/worldcup/games/current");

	private final WorldcupGameService gameService;
	private final AnonymousVisitorCookie visitorCookie;

	public WorldcupGameController(
		WorldcupGameService gameService,
		AnonymousVisitorCookie visitorCookie
	) {
		this.gameService = gameService;
		this.visitorCookie = visitorCookie;
	}

	@PostMapping("/games")
	@Operation(summary = "배너에서 월드컵 게임 시작")
	@ApiResponse(responseCode = "201", description = "게임 생성")
	@ApiResponse(
		responseCode = "409", description = "투표 가능한 대진 부족",
		content = @Content(schema = @Schema(implementation = ApiErrorResponse.class))
	)
	public ResponseEntity<WorldcupGameResponse> startFromBanner(
		@CookieValue(name = AnonymousVisitorCookie.NAME, required = false) String cookieValue
	) {
		AnonymousVisitorCookie.Visitor visitor = visitorCookie.identifyForStart(cookieValue);
		return created(gameService.startFromBanner(visitor.anonymousId()), visitor);
	}

	@PostMapping("/zones/{zoneId}/games")
	@Operation(summary = "선택한 권역에서 월드컵 게임 시작")
	@ApiResponse(responseCode = "201", description = "게임 생성")
	@ApiResponse(
		responseCode = "409", description = "투표 가능한 대진 부족",
		content = @Content(schema = @Schema(implementation = ApiErrorResponse.class))
	)
	public ResponseEntity<WorldcupGameResponse> startForZone(
		@PathVariable String zoneId,
		@CookieValue(name = AnonymousVisitorCookie.NAME, required = false) String cookieValue
	) {
		AnonymousVisitorCookie.Visitor visitor = visitorCookie.identifyForStart(cookieValue);
		return created(gameService.startForZone(visitor.anonymousId(), zoneId), visitor);
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

	private ResponseEntity<WorldcupGameResponse> created(
		GeneratedMatchGame game,
		AnonymousVisitorCookie.Visitor visitor
	) {
		ResponseEntity.BodyBuilder response = ResponseEntity.created(CURRENT_GAME_URI)
			.cacheControl(CacheControl.noStore());
		if (visitor.setCookie() != null) {
			response.header(HttpHeaders.SET_COOKIE, visitor.setCookie());
		}
		return response.body(WorldcupGameResponse.from(game));
	}
}
