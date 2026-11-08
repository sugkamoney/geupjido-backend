package com.geupjido.match.api;

import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.bind.annotation.RestControllerAdvice;

import com.geupjido.match.service.MatchUnavailableException;
import com.geupjido.match.service.WorldcupDailyLimitReachedException;
import com.geupjido.match.service.WorldcupGameNotFoundException;
import com.geupjido.match.service.WorldcupVoteRejectedException;

@RestControllerAdvice(assignableTypes = WorldcupGameController.class)
public class WorldcupGameExceptionHandler {

	@ExceptionHandler(MatchUnavailableException.class)
	public ResponseEntity<ApiErrorResponse> handleUnavailable(MatchUnavailableException exception) {
		return ResponseEntity.status(HttpStatus.CONFLICT)
			.body(new ApiErrorResponse("GAME_UNAVAILABLE", exception.getMessage()));
	}

	@ExceptionHandler(WorldcupDailyLimitReachedException.class)
	public ResponseEntity<ApiErrorResponse> handleDailyLimit(WorldcupDailyLimitReachedException exception) {
		return ResponseEntity.status(HttpStatus.TOO_MANY_REQUESTS)
			.body(new ApiErrorResponse("DAILY_GAME_LIMIT_REACHED", exception.getMessage()));
	}

	@ExceptionHandler(WorldcupGameNotFoundException.class)
	public ResponseEntity<ApiErrorResponse> handleNotFound(WorldcupGameNotFoundException exception) {
		return ResponseEntity.status(HttpStatus.NOT_FOUND)
			.body(new ApiErrorResponse("GAME_NOT_FOUND", exception.getMessage()));
	}

	@ExceptionHandler(WorldcupVoteRejectedException.class)
	public ResponseEntity<ApiErrorResponse> handleVoteRejected(WorldcupVoteRejectedException exception) {
		HttpStatus status = "INVALID_VOTE".equals(exception.code())
			? HttpStatus.BAD_REQUEST : HttpStatus.CONFLICT;
		return ResponseEntity.status(status)
			.body(new ApiErrorResponse(exception.code(), exception.getMessage()));
	}
}
