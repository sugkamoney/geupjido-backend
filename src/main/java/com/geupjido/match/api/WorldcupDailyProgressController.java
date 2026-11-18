package com.geupjido.match.api;

import org.springframework.http.CacheControl;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.CookieValue;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;

import com.geupjido.match.service.WorldcupDailyProgressService;
import com.geupjido.user.api.AnonymousVisitorCookie;

@RestController
@RequestMapping(path = "/api/v1/worldcup/progress", produces = MediaType.APPLICATION_JSON_VALUE)
@Tag(name = "월드컵")
public class WorldcupDailyProgressController {

	private final WorldcupDailyProgressService service;
	private final AnonymousVisitorCookie visitorCookie;

	public WorldcupDailyProgressController(WorldcupDailyProgressService service,
		AnonymousVisitorCookie visitorCookie) {
		this.service = service;
		this.visitorCookie = visitorCookie;
	}

	@GetMapping
	@Operation(summary = "오늘 일반 월드컵 진행 판과 남은 판 조회")
	public ResponseEntity<WorldcupDailyProgressResponse> get(
		@CookieValue(name = AnonymousVisitorCookie.NAME, required = false) String cookieValue) {
		var progress = service.get(visitorCookie.identify(cookieValue).orElse(null));
		return ResponseEntity.ok().cacheControl(CacheControl.noStore())
			.body(WorldcupDailyProgressResponse.from(progress));
	}
}
