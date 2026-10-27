package com.geupjido.batch.complex.exception;

/**
 * 지오코딩 후보가 여러 건이라 단지를 자동 배정할 수 없을 때 발생한다.
 */
public class MultipleGeocodingResultsException extends IllegalStateException {

	public MultipleGeocodingResultsException() {
		super("지오코딩 결과가 여러 건이어서 자동 선택할 수 없습니다.");
	}
}
