package com.geupjido.batch.complex.exception;

/**
 * 공동주택 기본정보 API 응답을 도메인 객체로 변환할 수 없을 때 발생한다.
 */
public class ComplexBasicInfoMappingException extends RuntimeException {

	public ComplexBasicInfoMappingException(String message) {
		super(message);
	}

	public ComplexBasicInfoMappingException(
		String message,
		Throwable cause
	) {
		super(message, cause);
	}
}
