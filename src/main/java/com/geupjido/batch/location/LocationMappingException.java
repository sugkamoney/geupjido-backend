package com.geupjido.batch.location;

/**
 * 지역·권역 매핑 데이터가 올바르지 않을 때 발생한다.
 */
public class LocationMappingException extends RuntimeException {

	public LocationMappingException(String message) {
		super(message);
	}

	public LocationMappingException(String message, Throwable cause) {
		super(message, cause);
	}
}
