package com.geupjido.batch.complex.model;

/**
 * 공동주택 기본정보 일괄 적재 중 실패한 단지 정보를 나타낸다.
 */
public record ComplexImportFailure(
	String complexCode,
	ComplexImportFailureType type,
	String reason
) {
}
