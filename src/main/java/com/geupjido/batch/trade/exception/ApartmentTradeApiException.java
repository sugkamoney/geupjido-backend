package com.geupjido.batch.trade.exception;

/**
 * 아파트 매매 실거래가 API 호출 또는 응답 처리에 실패했을 때 발생한다.
 */
public class ApartmentTradeApiException extends RuntimeException {

	public ApartmentTradeApiException(String message) {
		super(message);
	}

	public ApartmentTradeApiException(
		String message,
		Throwable cause
	) {
		super(message, cause);
	}
}
