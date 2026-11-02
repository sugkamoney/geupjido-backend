package com.geupjido.batch.trade.service;

import com.geupjido.trade.domain.ApartmentTrade;

/** 동일 계약 조건의 정상·해제 행을 별도 원본으로 보존할 최소 증거를 확인한다. */
final class ApartmentTradeSourceIdentity {
	private ApartmentTradeSourceIdentity() {
	}

	static boolean isRegisteredAfterCancellation(
		ApartmentTrade normal, ApartmentTrade canceled
	) {
		return !normal.isCanceled()
			&& canceled.isCanceled()
			&& normal.getRegistrationDate() != null
			&& normal.getApartmentDong() != null
			&& !normal.getApartmentDong().isBlank()
			&& canceled.getRegistrationDate() == null
			&& normal.getRegistrationDate().isAfter(
				canceled.getCancellationDate());
	}
}
