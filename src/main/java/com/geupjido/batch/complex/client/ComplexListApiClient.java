package com.geupjido.batch.complex.client;

import java.util.ArrayList;
import java.util.List;
import java.util.regex.Pattern;

import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.stereotype.Component;
import org.springframework.web.client.RestClient;
import org.springframework.web.client.RestClientException;

import com.geupjido.batch.complex.config.ComplexListApiProperties;
import com.geupjido.batch.complex.dto.ComplexListApiItem;
import com.geupjido.batch.complex.dto.ComplexListApiResponse;
import com.geupjido.batch.complex.exception.ComplexListApiException;

/**
 * 법정동 코드를 기준으로 공동주택 단지 목록 API를 호출한다.
 */
@Component
@ConditionalOnProperty(
	prefix = "app.external.complex-list",
	name = "enabled",
	havingValue = "true"
)
public class ComplexListApiClient {

	private static final String SUCCESS_RESULT_CODE = "00";
	private static final int PAGE_SIZE = 100;

	private static final String LEGAL_DONG_LIST_PATH =
		"/getLegaldongAptList4";

	private static final Pattern LEGAL_DONG_CODE_PATTERN =
		Pattern.compile("\\d{10}");

	private final RestClient restClient;
	private final ComplexListApiProperties properties;

	public ComplexListApiClient(
		@Qualifier("complexListRestClient") RestClient restClient,
		ComplexListApiProperties properties
	) {
		this.restClient = restClient;
		this.properties = properties;
	}

	public List<ComplexListApiItem> fetchAllByLegalDongCode(
		String legalDongCode
	) {
		validateLegalDongCode(legalDongCode);

		List<ComplexListApiItem> items = new ArrayList<>();
		int pageNo = 1;
		int totalCount;

		do {
			ComplexListApiResponse apiResponse = requestPage(
				legalDongCode,
				pageNo,
				PAGE_SIZE
			);
			ComplexListApiResponse.Body body =
				requireSuccessfulBody(apiResponse);

			if (body.items().isEmpty() && items.size() < body.totalCount()) {
				throw new ComplexListApiException(
					"전체 조회가 끝나기 전에 빈 페이지가 반환되었습니다."
				);
			}

			items.addAll(body.items());
			totalCount = body.totalCount();
			pageNo++;
		} while (items.size() < totalCount);

		return List.copyOf(items);
	}

	private ComplexListApiResponse requestPage(
		String legalDongCode,
		int pageNo,
		int numOfRows
	) {
		try {
			return restClient.get()
				.uri(uriBuilder -> uriBuilder
					.path(LEGAL_DONG_LIST_PATH)
					.queryParam("serviceKey", properties.serviceKey())
					.queryParam("pageNo", pageNo)
					.queryParam("numOfRows", numOfRows)
					.queryParam("bjdCode", legalDongCode)
					.build()
				)
				.retrieve()
				.body(ComplexListApiResponse.class);
		} catch (RestClientException exception) {
			throw new ComplexListApiException(
				"공동주택 단지 목록 API 호출에 실패했습니다.",
				exception
			);
		}
	}

	private static ComplexListApiResponse.Body requireSuccessfulBody(
		ComplexListApiResponse apiResponse
	) {
		if (
			apiResponse == null
				|| apiResponse.response() == null
				|| apiResponse.response().header() == null
		) {
			throw new ComplexListApiException(
				"공동주택 단지 목록 API 응답 구조가 올바르지 않습니다."
			);
		}

		ComplexListApiResponse.Result result = apiResponse.response();
		ComplexListApiResponse.Header header = result.header();

		if (!SUCCESS_RESULT_CODE.equals(header.resultCode())) {
			throw new ComplexListApiException(
				"공동주택 단지 목록 API가 오류를 반환했습니다. "
					+ "resultCode=" + header.resultCode()
					+ ", resultMsg=" + header.resultMsg()
			);
		}

		if (result.body() == null) {
			throw new ComplexListApiException(
				"공동주택 단지 목록 API 응답에 body가 없습니다."
			);
		}

		return result.body();
	}

	private static void validateLegalDongCode(String legalDongCode) {
		if (
			legalDongCode == null
				|| !LEGAL_DONG_CODE_PATTERN.matcher(legalDongCode).matches()
		) {
			throw new IllegalArgumentException(
				"법정동 코드는 숫자 10자리여야 합니다."
			);
		}
	}
}
