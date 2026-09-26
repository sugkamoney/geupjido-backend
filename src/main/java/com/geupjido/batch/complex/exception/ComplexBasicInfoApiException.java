package com.geupjido.batch.complex.exception;

/**
 * 공동주택 단지 기본정보 API 호출 또는 응답 처리에 실패했을 때 발생한다.
 */
public class ComplexBasicInfoApiException extends RuntimeException {

	public ComplexBasicInfoApiException(String message) {
		super(message);
	}

	public ComplexBasicInfoApiException(
		String message,
		Throwable cause
	) {
		super(message, cause);
	}
}
