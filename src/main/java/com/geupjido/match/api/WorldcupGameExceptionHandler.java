package com.geupjido.match.api;

import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.bind.annotation.RestControllerAdvice;

import com.geupjido.match.service.MatchUnavailableException;
import com.geupjido.match.service.WorldcupGameNotFoundException;

@RestControllerAdvice(assignableTypes = WorldcupGameController.class)
public class WorldcupGameExceptionHandler {

	@ExceptionHandler(MatchUnavailableException.class)
	public ResponseEntity<ApiErrorResponse> handleUnavailable(MatchUnavailableException exception) {
		return ResponseEntity.status(HttpStatus.CONFLICT)
			.body(new ApiErrorResponse("GAME_UNAVAILABLE", exception.getMessage()));
	}

	@ExceptionHandler(WorldcupGameNotFoundException.class)
	public ResponseEntity<ApiErrorResponse> handleNotFound(WorldcupGameNotFoundException exception) {
		return ResponseEntity.status(HttpStatus.NOT_FOUND)
			.body(new ApiErrorResponse("GAME_NOT_FOUND", exception.getMessage()));
	}
}
