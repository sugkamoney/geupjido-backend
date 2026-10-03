package com.geupjido.batch.trade.exception;

/**
 * 아파트 매매 실거래가 API 응답을 내부 모델로 변환하지 못했을 때 발생한다.
 */
public class ApartmentTradeMappingException extends RuntimeException {

	public ApartmentTradeMappingException(String message) {
		super(message);
	}

	public ApartmentTradeMappingException(String message, Throwable cause) {
		super(message, cause);
	}
}
