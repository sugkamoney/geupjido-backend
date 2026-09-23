package com.geupjido.batch.location;

/**
 * GeoJSON에서 읽은 법정동 코드와 경계 도형을 나타낸다.
 */
public record LegalDongBoundary(
	String dongCode,
	String geometryJson
) {
}
