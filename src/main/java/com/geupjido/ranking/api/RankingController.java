package com.geupjido.ranking.api;

import org.springframework.http.CacheControl;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import com.geupjido.ranking.model.RankingView;
import com.geupjido.ranking.model.RankingView.Region;
import com.geupjido.ranking.model.RankingView.Sort;
import com.geupjido.ranking.model.RankingView.Tab;
import com.geupjido.ranking.service.RankingQueryService;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;

@RestController
@RequestMapping(path = "/api/v1/rankings", produces = MediaType.APPLICATION_JSON_VALUE)
@Tag(name = "랭킹")
public class RankingController {
	private final RankingQueryService service;

	public RankingController(RankingQueryService service) {
		this.service = service;
	}

	@GetMapping("/zones")
	@Operation(summary = "랭킹 탭 권역 순위 조회")
	public ResponseEntity<RankingView.Response> zones(
		@RequestParam(defaultValue = "ALL") Region region,
		@RequestParam(defaultValue = "GRADE_1") Tab tab,
		@RequestParam(defaultValue = "CURRENT") Sort sort
	) {
		return ResponseEntity.ok().cacheControl(CacheControl.noStore())
			.body(service.zones(region, tab, sort));
	}
}
