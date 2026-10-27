package com.geupjido.batch.complex.exception;

/**
 * 공동주택 단지 목록 API 호출 또는 응답 처리에 실패했을 때 발생한다.
 */
public class ComplexListApiException extends RuntimeException {
	private final String resultCode;

	public ComplexListApiException(String message) {
		super(message);
		this.resultCode = null;
	}

	public ComplexListApiException(String message, String resultCode) {
		super(message);
		this.resultCode = resultCode;
	}

	public ComplexListApiException(String message, Throwable cause) {
		super(message, cause);
		this.resultCode = null;
	}

	public String resultCode() {
		return resultCode;
	}
}
