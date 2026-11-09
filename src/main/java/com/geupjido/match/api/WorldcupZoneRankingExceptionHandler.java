package com.geupjido.match.api;

import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.bind.annotation.RestControllerAdvice;

import com.geupjido.match.service.WorldcupZoneRankingNotFoundException;

@RestControllerAdvice(assignableTypes = WorldcupZoneRankingController.class)
public class WorldcupZoneRankingExceptionHandler {

	@ExceptionHandler(WorldcupZoneRankingNotFoundException.class)
	public ResponseEntity<ApiErrorResponse> handleNotFound(
		WorldcupZoneRankingNotFoundException exception
	) {
		return ResponseEntity.status(HttpStatus.NOT_FOUND)
			.body(new ApiErrorResponse("ZONE_NOT_FOUND", exception.getMessage()));
	}
}
