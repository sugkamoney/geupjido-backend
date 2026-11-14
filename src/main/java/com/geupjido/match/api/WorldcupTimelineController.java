package com.geupjido.match.api;

import java.util.UUID;

import org.springframework.http.CacheControl;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.CookieValue;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;

import com.geupjido.match.service.WorldcupTimelineService;
import com.geupjido.user.api.AnonymousVisitorCookie;

@RestController
@RequestMapping(path = "/api/v1/worldcup", produces = MediaType.APPLICATION_JSON_VALUE)
@Tag(name = "월드컵 타임라인", description = "최근에 선택된 월드컵 대진을 조회합니다.")
public class WorldcupTimelineController {

	private final WorldcupTimelineService service;
	private final AnonymousVisitorCookie visitorCookie;

	public WorldcupTimelineController(WorldcupTimelineService service,
		AnonymousVisitorCookie visitorCookie) {
		this.service = service;
		this.visitorCookie = visitorCookie;
	}

	@GetMapping("/timeline")
	@Operation(summary = "최근 월드컵 선택 20건 조회",
		description = "zoneId를 주면 해당 권역이 대진의 어느 쪽에든 나온 선택만 반환합니다.")
	public ResponseEntity<WorldcupTimelineResponse> recent(
		@RequestParam(required = false) String zoneId,
		@CookieValue(name = AnonymousVisitorCookie.NAME, required = false) String cookieValue
	) {
		UUID currentVisitorId = visitorCookie.identify(cookieValue).orElse(null);
		return ResponseEntity.ok().cacheControl(CacheControl.noStore())
			.body(WorldcupTimelineResponse.from(service.recent(zoneId, currentVisitorId)));
	}
}
