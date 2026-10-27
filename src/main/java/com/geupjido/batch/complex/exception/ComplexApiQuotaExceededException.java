package com.geupjido.batch.complex.exception;

/**
 * 공공데이터 API의 일일 호출 한도가 소진되어 이번 적재를 계속할 수 없을 때 발생한다.
 */
public class ComplexApiQuotaExceededException extends RuntimeException {

	public ComplexApiQuotaExceededException(String message, Throwable cause) {
		super(message, cause);
	}
}
