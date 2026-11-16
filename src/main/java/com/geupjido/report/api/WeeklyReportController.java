package com.geupjido.report.api;

import org.springframework.http.CacheControl;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import com.geupjido.report.service.WeeklyReportQueryService;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;

@RestController
@RequestMapping(path = "/api/v1/reports", produces = MediaType.APPLICATION_JSON_VALUE)
@Tag(name = "리포트")
public class WeeklyReportController {
	private final WeeklyReportQueryService service;

	public WeeklyReportController(WeeklyReportQueryService service) {
		this.service = service;
	}

	@GetMapping
	@Operation(summary = "공개된 주간 리포트 목록 조회",
		description = "최신순 목록과 첫 발행 전·준비 중·지연·발행 상태를 반환합니다. "
			+ "첫 발행 전에는 마지막 급지 집계 기준 수집 중 권역도 제공합니다. 초안은 노출하지 않습니다.")
	public ResponseEntity<WeeklyReportResponse.ListResponse> list() {
		return ResponseEntity.ok().cacheControl(CacheControl.noStore()).body(service.list());
	}

	@GetMapping("/{reportId}")
	@Operation(summary = "주간 민심 급지 리포트 상세 조회",
		description = "저장된 주간 스냅샷으로 상승·하락, 급지 경계, 수집 중 권역과 구별 변화를 조회합니다.")
	public ResponseEntity<WeeklyReportResponse.Detail> detail(@PathVariable long reportId) {
		return ResponseEntity.ok().cacheControl(CacheControl.noStore())
			.body(service.detail(reportId));
	}
}
