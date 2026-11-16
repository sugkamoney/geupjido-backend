package com.geupjido.report.api;

import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.bind.annotation.RestControllerAdvice;
import org.springframework.web.method.annotation.MethodArgumentTypeMismatchException;

import com.geupjido.match.api.ApiErrorResponse;
import com.geupjido.report.service.WeeklyReportNotFoundException;

@RestControllerAdvice(assignableTypes = WeeklyReportController.class)
public class WeeklyReportExceptionHandler {
	@ExceptionHandler(WeeklyReportNotFoundException.class)
	public ResponseEntity<ApiErrorResponse> notFound(WeeklyReportNotFoundException exception) {
		return ResponseEntity.status(404)
			.body(new ApiErrorResponse("REPORT_NOT_FOUND", exception.getMessage()));
	}

	@ExceptionHandler({IllegalArgumentException.class, MethodArgumentTypeMismatchException.class})
	public ResponseEntity<ApiErrorResponse> invalidRequest(Exception exception) {
		return ResponseEntity.badRequest()
			.body(new ApiErrorResponse("INVALID_REQUEST", "리포트 번호를 확인해 주세요."));
	}
}
