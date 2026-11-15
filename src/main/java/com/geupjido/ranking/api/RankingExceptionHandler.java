package com.geupjido.ranking.api;

import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.bind.annotation.RestControllerAdvice;
import org.springframework.web.method.annotation.MethodArgumentTypeMismatchException;

import com.geupjido.match.api.ApiErrorResponse;

@RestControllerAdvice(assignableTypes = RankingController.class)
public class RankingExceptionHandler {
	@ExceptionHandler(IllegalArgumentException.class)
	public ResponseEntity<ApiErrorResponse> invalidRequest(IllegalArgumentException exception) {
		return ResponseEntity.badRequest()
			.body(new ApiErrorResponse("INVALID_REQUEST", exception.getMessage()));
	}

	@ExceptionHandler(MethodArgumentTypeMismatchException.class)
	public ResponseEntity<ApiErrorResponse> invalidParameter(Exception exception) {
		return ResponseEntity.badRequest()
			.body(new ApiErrorResponse("INVALID_REQUEST", "지역·급지 탭·정렬을 확인해 주세요."));
	}
}
