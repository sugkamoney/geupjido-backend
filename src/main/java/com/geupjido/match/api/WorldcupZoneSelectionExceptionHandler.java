package com.geupjido.match.api;

import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.bind.annotation.RestControllerAdvice;

import com.geupjido.match.service.WorldcupZoneSelectionNotFoundException;

@RestControllerAdvice(assignableTypes = WorldcupZoneSelectionController.class)
public class WorldcupZoneSelectionExceptionHandler {

	@ExceptionHandler(WorldcupZoneSelectionNotFoundException.class)
	public ResponseEntity<ApiErrorResponse> handleNotFound(
		WorldcupZoneSelectionNotFoundException exception
	) {
		return ResponseEntity.status(HttpStatus.NOT_FOUND)
			.body(new ApiErrorResponse("ZONE_NOT_FOUND", exception.getMessage()));
	}
}
