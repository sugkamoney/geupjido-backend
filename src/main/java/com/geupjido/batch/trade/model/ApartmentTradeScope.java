package com.geupjido.batch.trade.model;

/**
 * 실거래가의 법정동이 현재 서비스 권역에 속하는지 나타낸다.
 */
public record ApartmentTradeScope(
	Status status,
	String legalDongCode,
	String zoneId
) {
	public enum Status {
		IN_SCOPE,
		OUT_OF_SCOPE,
		UNKNOWN_LEGAL_DONG
	}

	public ApartmentTradeScope {
		if (status == null) {
			throw new IllegalArgumentException("서비스 범위 상태가 없습니다.");
		}
		if (status == Status.UNKNOWN_LEGAL_DONG) {
			if (legalDongCode != null || zoneId != null) {
				throw new IllegalArgumentException(
					"법정동 코드 미확인 상태에는 코드와 권역이 없어야 합니다."
				);
			}
		} else if (legalDongCode == null) {
			throw new IllegalArgumentException(
				"서비스 범위를 판별한 거래에는 법정동 코드가 필요합니다."
			);
		} else if ((status == Status.IN_SCOPE) != (zoneId != null)) {
			throw new IllegalArgumentException(
				"대상 거래에만 권역 ID가 있어야 합니다."
			);
		}
	}
}
