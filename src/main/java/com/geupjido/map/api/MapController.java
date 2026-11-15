package com.geupjido.map.api;

import java.util.List;

import org.springframework.http.CacheControl;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.Parameter;
import io.swagger.v3.oas.annotations.media.ArraySchema;
import io.swagger.v3.oas.annotations.media.Content;
import io.swagger.v3.oas.annotations.media.Schema;
import io.swagger.v3.oas.annotations.responses.ApiResponse;
import io.swagger.v3.oas.annotations.tags.Tag;

import com.geupjido.map.model.MapView;
import com.geupjido.map.service.MapQueryService;
import com.geupjido.match.api.ApiErrorResponse;

/**
 * 지도 탭의 HTTP 진입점이다. 이 클래스는 요청 값을 받거나 JSON 응답으로 바꾸고,
 * 검색·급지·순위 판단은 Service와 Repository에 맡긴다.
 */
@RestController
@RequestMapping(path = "/api/v1/map", produces = MediaType.APPLICATION_JSON_VALUE)
// Swagger에서 새 조회 API가 '지도' 탭 아래 보이도록 묶는다.
@Tag(name = "지도")
public class MapController {

	private final MapQueryService service;
	private final ObjectMapper objectMapper;

	public MapController(MapQueryService service, ObjectMapper objectMapper) {
		this.service = service;
		this.objectMapper = objectMapper;
	}

	@GetMapping("/cities")
	@Operation(summary = "지도 구·시 라벨 조회")
	public ResponseEntity<List<MapView.City>> cities() {
		return noStore(service.cities());
	}

	@GetMapping("/zones")
	@Operation(summary = "화면 범위의 권역 라벨 조회")
	@ApiResponse(responseCode = "200", description = "권역 목록",
		content = @Content(array = @ArraySchema(schema = @Schema(implementation = MapView.Zone.class))))
	@ApiResponse(responseCode = "400", description = "잘못된 지도 범위",
		content = @Content(schema = @Schema(implementation = ApiErrorResponse.class)))
	public ResponseEntity<List<MapView.Zone>> zones(@RequestParam double west,
		@RequestParam double south, @RequestParam double east,
		@RequestParam double north) {
		// 브라우저가 보고 있는 사각형만 전달한다. Bounds 생성자에서 좌표 범위를 검증한다.
		return noStore(service.zones(new MapView.Bounds(west, south, east, north)));
	}

	@GetMapping("/complexes")
	@Operation(summary = "화면 범위의 단지 핀 조회")
	@ApiResponse(responseCode = "200", description = "단지 핀 목록",
		content = @Content(array = @ArraySchema(schema = @Schema(implementation = MapView.ComplexPin.class))))
	@ApiResponse(responseCode = "400", description = "잘못된 지도 범위",
		content = @Content(schema = @Schema(implementation = ApiErrorResponse.class)))
	public ResponseEntity<List<MapView.ComplexPin>> complexes(@RequestParam double west,
		@RequestParam double south, @RequestParam double east,
		@RequestParam double north) {
		return noStore(service.pins(new MapView.Bounds(west, south, east, north)));
	}

	@GetMapping("/zones/{zoneId}")
	@Operation(summary = "권역 경계와 전체 단지 상세 조회")
	@ApiResponse(responseCode = "200", description = "권역 상세",
		content = @Content(schema = @Schema(implementation = ZoneDetailResponse.class)))
	@ApiResponse(responseCode = "404", description = "권역 없음: ZONE_NOT_FOUND",
		content = @Content(schema = @Schema(implementation = ApiErrorResponse.class)))
	public ResponseEntity<ZoneDetailResponse> zone(@PathVariable String zoneId) {
		MapView.ZoneDetail detail = service.zone(zoneId);
		try {
			// DB의 ST_AsGeoJSON 문자열을 실제 MultiPolygon 응답 구조로 읽는다.
			return noStore(new ZoneDetailResponse(detail.zone(),
				objectMapper.readValue(detail.polygonGeoJson(), GeoJsonMultiPolygon.class),
				detail.complexes()));
		} catch (JsonProcessingException exception) {
			throw new IllegalStateException("권역 경계 GeoJSON을 읽을 수 없습니다.", exception);
		}
	}

	@GetMapping("/search")
	@Operation(summary = "구·권역·단지 검색")
	@ApiResponse(responseCode = "200", description = "검색 결과",
		content = @Content(schema = @Schema(implementation = MapView.SearchResult.class)))
	@ApiResponse(responseCode = "400", description = "잘못된 검색어: INVALID_REQUEST",
		content = @Content(schema = @Schema(implementation = ApiErrorResponse.class)))
	public ResponseEntity<MapView.SearchResult> search(
		@Parameter(description = "공백을 포함해 1~80자. 공백만 입력할 수 없음",
			schema = @Schema(minLength = 1, maxLength = 80))
		@RequestParam String q) {
		return noStore(service.search(q));
	}

	@GetMapping("/status")
	@Operation(summary = "지도 집계 상태 카드 조회")
	public ResponseEntity<MapView.Status> status() {
		return noStore(service.status());
	}

	private static <T> ResponseEntity<T> noStore(T body) {
		// 급지와 표 수는 집계 후 달라질 수 있어 브라우저의 오래된 캐시를 쓰지 않는다.
		return ResponseEntity.ok().cacheControl(CacheControl.noStore()).body(body);
	}

	public record ZoneDetailResponse(
		@Schema(requiredMode = Schema.RequiredMode.REQUIRED) MapView.Zone zone,
		@Schema(requiredMode = Schema.RequiredMode.REQUIRED) GeoJsonMultiPolygon polygon,
		@Schema(requiredMode = Schema.RequiredMode.REQUIRED) List<MapView.Complex> complexes) {
	}

	@Schema(name = "GeoJsonMultiPolygon", description = "WGS84 GeoJSON MultiPolygon. 내부 링은 hole이다.")
	public record GeoJsonMultiPolygon(
		@Schema(allowableValues = "MultiPolygon", requiredMode = Schema.RequiredMode.REQUIRED)
		String type,
		@Schema(description = "다각형 → 링 → 경도·위도 좌표", requiredMode = Schema.RequiredMode.REQUIRED)
		List<List<List<List<Double>>>> coordinates) {
	}
}
