package com.geupjido.map.api;

import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.bind.annotation.RestControllerAdvice;
import org.springframework.web.bind.MissingServletRequestParameterException;
import org.springframework.web.method.annotation.MethodArgumentTypeMismatchException;

import com.geupjido.map.service.MapZoneNotFoundException;
import com.geupjido.match.api.ApiErrorResponse;

// 지도 컨트롤러의 오류만 처리해 기존 월드컵 API의 오류 규칙에 영향을 주지 않는다.
@RestControllerAdvice(assignableTypes = MapController.class)
public class MapExceptionHandler {

	@ExceptionHandler(MapZoneNotFoundException.class)
	public ResponseEntity<ApiErrorResponse> zoneNotFound(MapZoneNotFoundException exception) {
		return ResponseEntity.status(HttpStatus.NOT_FOUND)
			.body(new ApiErrorResponse("ZONE_NOT_FOUND", exception.getMessage()));
	}

	@ExceptionHandler(IllegalArgumentException.class)
	public ResponseEntity<ApiErrorResponse> invalidRequest(IllegalArgumentException exception) {
		return ResponseEntity.badRequest()
			.body(new ApiErrorResponse("INVALID_REQUEST", exception.getMessage()));
	}

	@ExceptionHandler({MissingServletRequestParameterException.class,
		MethodArgumentTypeMismatchException.class})
	public ResponseEntity<ApiErrorResponse> invalidParameter(Exception exception) {
		return ResponseEntity.badRequest()
			.body(new ApiErrorResponse("INVALID_REQUEST", "필수 요청 매개변수를 확인해 주세요."));
	}
}
