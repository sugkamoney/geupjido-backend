package com.geupjido.batch.complex.client;

import java.util.regex.Pattern;

import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.http.MediaType;
import org.springframework.stereotype.Component;
import org.springframework.web.client.RestClient;
import org.springframework.web.client.RestClientException;
import org.springframework.web.client.RestClientResponseException;

import com.geupjido.batch.complex.config.ComplexBasicInfoApiProperties;
import com.geupjido.batch.complex.dto.ComplexBasicInfoApiItem;
import com.geupjido.batch.complex.dto.ComplexBasicInfoApiResponse;
import com.geupjido.batch.complex.exception.ComplexBasicInfoApiException;
import com.geupjido.batch.complex.service.ComplexApiPacer;

/**
 * 단지 코드를 기준으로 공동주택 단지 기본정보 API를 호출한다.
 */
@Component
@ConditionalOnProperty(
	prefix = "app.external.complex-basic-info",
	name = "enabled",
	havingValue = "true"
)
public class ComplexBasicInfoApiClient {

	private static final String SUCCESS_RESULT_CODE = "00";

	private static final String BASIC_INFO_PATH =
		"/getAphusBassInfoV5";

	private static final Pattern COMPLEX_CODE_PATTERN =
		Pattern.compile("[A-Za-z0-9]{1,20}");

	private final RestClient restClient;
	private final ComplexBasicInfoApiProperties properties;
	private final ComplexApiPacer apiPacer;

	public ComplexBasicInfoApiClient(
		@Qualifier("complexBasicInfoRestClient") RestClient restClient,
		ComplexBasicInfoApiProperties properties,
		ComplexApiPacer apiPacer
	) {
		this.restClient = restClient;
		this.properties = properties;
		this.apiPacer = apiPacer;
	}

	public ComplexBasicInfoApiItem fetchByComplexCode(
		String complexCode
	) {
		validateComplexCode(complexCode);

		ComplexBasicInfoApiResponse apiResponse =
			requestBasicInfo(complexCode);

		return requireSuccessfulItem(apiResponse);
	}

	private ComplexBasicInfoApiResponse requestBasicInfo(
		String complexCode
	) {
		try {
			apiPacer.beforeRequest();
			return restClient.get()
				.uri(uriBuilder -> uriBuilder
					.path(BASIC_INFO_PATH)
					.queryParam("serviceKey", properties.serviceKey())
					.queryParam("kaptCode", complexCode)
					.build()
				)
				.retrieve()
				.body(ComplexBasicInfoApiResponse.class);
		} catch (RestClientResponseException exception) {
			throw new ComplexBasicInfoApiException(
				"공동주택 단지 기본정보 API가 HTTP 오류를 반환했습니다.",
				ComplexApiHttpErrorCode.extract(exception)
			);
		} catch (RestClientException exception) {
			throw new ComplexBasicInfoApiException(
				"공동주택 단지 기본정보 API 호출에 실패했습니다.",
				exception
			);
		}
	}

	private static ComplexBasicInfoApiItem requireSuccessfulItem(
		ComplexBasicInfoApiResponse apiResponse
	) {
		if (
			apiResponse != null
				&& apiResponse.gatewayError() != null
				&& apiResponse.gatewayError().cmmMsgHeader() != null
		) {
			var error = apiResponse.gatewayError().cmmMsgHeader();
			String resultCode = ComplexApiHttpErrorCode.normalize(
				error.returnReasonCode()
			);
			throw new ComplexBasicInfoApiException(
				"공동주택 단지 기본정보 API가 오류를 반환했습니다. "
					+ "resultCode=" + resultCode,
				resultCode
			);
		}

		if (
			apiResponse == null
				|| apiResponse.response() == null
				|| apiResponse.response().header() == null
		) {
			throw new ComplexBasicInfoApiException(
				"공동주택 단지 기본정보 API 응답 구조가 올바르지 않습니다."
			);
		}

		ComplexBasicInfoApiResponse.Result result = apiResponse.response();
		ComplexBasicInfoApiResponse.Header header = result.header();

		if (!SUCCESS_RESULT_CODE.equals(header.resultCode())) {
			String resultCode = ComplexApiHttpErrorCode.normalize(
				header.resultCode()
			);
			throw new ComplexBasicInfoApiException(
				"공동주택 단지 기본정보 API가 오류를 반환했습니다. "
					+ "resultCode=" + resultCode,
				resultCode
			);
		}

		if (result.body() == null) {
			throw new ComplexBasicInfoApiException(
				"공동주택 단지 기본정보 API 응답에 body가 없습니다."
			);
		}

		if (result.body().item() == null) {
			throw new ComplexBasicInfoApiException(
				"공동주택 단지 기본정보 API 응답에 item이 없습니다."
			);
		}

		return result.body().item();
	}

	private static void validateComplexCode(String complexCode) {
		if (
			complexCode == null
				|| !COMPLEX_CODE_PATTERN.matcher(complexCode).matches()
		) {
			throw new IllegalArgumentException(
				"단지 코드는 1~20자의 영문자와 숫자로 구성되어야 합니다."
			);
		}
	}

}
