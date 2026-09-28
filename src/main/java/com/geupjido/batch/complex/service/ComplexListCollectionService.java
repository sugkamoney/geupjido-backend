package com.geupjido.batch.complex.service;

import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Set;
import java.util.regex.Pattern;

import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.stereotype.Service;

import com.geupjido.batch.complex.client.ComplexListApiClient;
import com.geupjido.batch.complex.dto.ComplexListApiItem;

/**
 * 여러 법정동 코드를 순회하며 공동주택 단지 목록을 수집한다.
 */
@Service
@ConditionalOnProperty(
	prefix = "app.external.complex-list",
	name = "enabled",
	havingValue = "true"
)
public class ComplexListCollectionService {

	private static final Pattern LEGAL_DONG_CODE_PATTERN =
		Pattern.compile("\\d{10}");

	private final ComplexListApiClient apiClient;

	public ComplexListCollectionService(
		ComplexListApiClient apiClient
	) {
		this.apiClient = apiClient;
	}

	/**
	 * 여러 법정동 코드에 속한 공동주택 단지 목록을 입력 순서대로 수집한다.
	 */
	public List<ComplexListApiItem> collectByLegalDongCodes(
		List<String> legalDongCodes
	) {
		validateLegalDongCodes(legalDongCodes);

		List<ComplexListApiItem> collectedItems = new ArrayList<>();

		for (String legalDongCode : legalDongCodes) {
			List<ComplexListApiItem> items =
				apiClient.fetchAllByLegalDongCode(legalDongCode);

			collectedItems.addAll(items);
		}

		return List.copyOf(collectedItems);
	}

	private static void validateLegalDongCodes(
		List<String> legalDongCodes
	) {
		if (legalDongCodes == null || legalDongCodes.isEmpty()) {
			throw new IllegalArgumentException(
				"법정동 코드 목록은 비어 있을 수 없습니다."
			);
		}

		Set<String> uniqueCodes = new HashSet<>();

		for (String legalDongCode : legalDongCodes) {
			if (
				legalDongCode == null
					|| !LEGAL_DONG_CODE_PATTERN
					.matcher(legalDongCode)
					.matches()
			) {
				throw new IllegalArgumentException(
					"법정동 코드는 숫자 10자리여야 합니다."
				);
			}

			if (!uniqueCodes.add(legalDongCode)) {
				throw new IllegalArgumentException(
					"중복된 법정동 코드가 있습니다: " + legalDongCode
				);
			}
		}
	}
}
