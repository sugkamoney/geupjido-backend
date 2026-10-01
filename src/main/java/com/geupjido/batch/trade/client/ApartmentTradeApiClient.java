package com.geupjido.batch.trade.client;

import java.util.ArrayList;
import java.util.List;
import java.util.regex.Pattern;

import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.stereotype.Component;
import org.springframework.web.client.RestClient;
import org.springframework.web.client.RestClientException;

import com.geupjido.batch.trade.config.ApartmentTradeApiProperties;
import com.geupjido.batch.trade.dto.ApartmentTradeApiItem;
import com.geupjido.batch.trade.dto.ApartmentTradeApiResponse;
import com.geupjido.batch.trade.exception.ApartmentTradeApiException;

/**
 * 시군구 코드와 계약월을 기준으로 아파트 매매 실거래가 API를 호출한다.
 */
@Component
@ConditionalOnProperty(
	prefix = "app.external.apartment-trade",
	name = "enabled",
	havingValue = "true"
)
public class ApartmentTradeApiClient {

	private static final String SUCCESS_RESULT_CODE = "000";
	private static final int PAGE_SIZE = 100;

	private static final String APARTMENT_TRADE_PATH =
		"/getRTMSDataSvcAptTrade";

	private static final Pattern DISTRICT_CODE_PATTERN =
		Pattern.compile("\\d{5}");

	private static final Pattern DEAL_YEAR_MONTH_PATTERN =
		Pattern.compile("\\d{4}(0[1-9]|1[0-2])");

	private final RestClient restClient;
	private final ApartmentTradeApiProperties properties;

	public ApartmentTradeApiClient(
		@Qualifier("apartmentTradeRestClient") RestClient restClient,
		ApartmentTradeApiProperties properties
	) {
		this.restClient = restClient;
		this.properties = properties;
	}

	public List<ApartmentTradeApiItem> fetchAll(
		String districtCode,
		String dealYearMonth
	) {
		validateDistrictCode(districtCode);
		validateDealYearMonth(dealYearMonth);

		List<ApartmentTradeApiItem> items = new ArrayList<>();
		int pageNo = 1;
		int totalCount;

		do {
			ApartmentTradeApiResponse apiResponse = requestPage(
				districtCode,
				dealYearMonth,
				pageNo,
				PAGE_SIZE
			);

			ApartmentTradeApiResponse.Body body =
				requireSuccessfulBody(apiResponse);

			if (
				body.tradeItems().isEmpty()
					&& items.size() < body.totalCount()
			) {
				throw new ApartmentTradeApiException(
					"전체 조회가 끝나기 전에 빈 페이지가 반환되었습니다."
				);
			}

			items.addAll(body.tradeItems());
			totalCount = body.totalCount();
			pageNo++;
		} while (items.size() < totalCount);

		return List.copyOf(items);
	}

	private ApartmentTradeApiResponse requestPage(
		String districtCode,
		String dealYearMonth,
		int pageNo,
		int numOfRows
	) {
		try {
			return restClient.get()
				.uri(uriBuilder -> uriBuilder
					.path(APARTMENT_TRADE_PATH)
					.queryParam("serviceKey", properties.serviceKey())
					.queryParam("LAWD_CD", districtCode)
					.queryParam("DEAL_YMD", dealYearMonth)
					.queryParam("pageNo", pageNo)
					.queryParam("numOfRows", numOfRows)
					.build()
				)
				.retrieve()
				.body(ApartmentTradeApiResponse.class);
		} catch (RestClientException exception) {
			throw new ApartmentTradeApiException(
				"아파트 매매 실거래가 API 호출에 실패했습니다.",
				exception
			);
		}
	}

	private static ApartmentTradeApiResponse.Body requireSuccessfulBody(
		ApartmentTradeApiResponse apiResponse
	) {
		if (apiResponse == null || apiResponse.header() == null) {
			throw new ApartmentTradeApiException(
				"아파트 매매 실거래가 API 응답 구조가 올바르지 않습니다."
			);
		}

		ApartmentTradeApiResponse.Header header = apiResponse.header();

		if (!SUCCESS_RESULT_CODE.equals(header.resultCode())) {
			throw new ApartmentTradeApiException(
				"아파트 매매 실거래가 API가 오류를 반환했습니다. "
					+ "resultCode=" + header.resultCode()
					+ ", resultMsg=" + header.resultMsg()
			);
		}

		if (apiResponse.body() == null) {
			throw new ApartmentTradeApiException(
				"아파트 매매 실거래가 API 응답에 body가 없습니다."
			);
		}

		return apiResponse.body();
	}

	private static void validateDistrictCode(String districtCode) {
		if (
			districtCode == null
				|| !DISTRICT_CODE_PATTERN.matcher(districtCode).matches()
		) {
			throw new IllegalArgumentException(
				"시군구 코드는 숫자 5자리여야 합니다."
			);
		}
	}

	private static void validateDealYearMonth(String dealYearMonth) {
		if (
			dealYearMonth == null
				|| !DEAL_YEAR_MONTH_PATTERN.matcher(dealYearMonth).matches()
		) {
			throw new IllegalArgumentException(
				"계약월은 yyyyMM 형식의 유효한 연월이어야 합니다."
			);
		}
	}
}
