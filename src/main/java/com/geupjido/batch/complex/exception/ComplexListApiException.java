package com.geupjido.batch.complex.exception;

/**
 * 공동주택 단지 목록 API 호출 또는 응답 처리에 실패했을 때 발생한다.
 */
public class ComplexListApiException extends RuntimeException {

	public ComplexListApiException(String message) {
		super(message);
	}

	public ComplexListApiException(String message, Throwable cause) {
		super(message, cause);
	}
}
