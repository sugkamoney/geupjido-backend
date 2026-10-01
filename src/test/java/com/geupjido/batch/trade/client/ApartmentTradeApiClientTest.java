package com.geupjido.batch.trade.client;

import java.util.List;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.http.HttpMethod;
import org.springframework.http.MediaType;
import org.springframework.test.web.client.MockRestServiceServer;
import org.springframework.web.client.RestClient;

import com.geupjido.batch.trade.config.ApartmentTradeApiProperties;
import com.geupjido.batch.trade.dto.ApartmentTradeApiItem;
import com.geupjido.batch.trade.exception.ApartmentTradeApiException;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.springframework.test.web.client.match.MockRestRequestMatchers.method;
import static org.springframework.test.web.client.match.MockRestRequestMatchers.requestTo;
import static org.springframework.test.web.client.response.MockRestResponseCreators.withSuccess;

class ApartmentTradeApiClientTest {

	private MockRestServiceServer server;
	private ApartmentTradeApiClient client;

	@BeforeEach
	void setUp() {
		RestClient.Builder builder = RestClient.builder()
			.baseUrl("https://example.com");

		server = MockRestServiceServer.bindTo(builder).build();

		ApartmentTradeApiProperties properties =
			new ApartmentTradeApiProperties(
				true,
				"https://example.com",
				"test-service-key"
			);

		client = new ApartmentTradeApiClient(
			builder.build(),
			properties
		);
	}

	@Test
	void 시군구_코드와_계약월로_실거래가를_조회한다() {
		server.expect(requestTo(
				"https://example.com/getRTMSDataSvcAptTrade"
					+ "?serviceKey=test-service-key"
					+ "&LAWD_CD=11170"
					+ "&DEAL_YMD=202609"
					+ "&pageNo=1"
					+ "&numOfRows=100"
			))
			.andExpect(method(HttpMethod.GET))
			.andRespond(withSuccess(
				"""
				<response>
					<header>
						<resultCode>000</resultCode>
						<resultMsg>OK</resultMsg>
					</header>
					<body>
						<items>
							<item>
								<aptDong>101</aptDong>
								<aptNm>한남테스트아파트</aptNm>
								<buildYear>2011</buildYear>
								<dealAmount>250,000</dealAmount>
								<dealDay>15</dealDay>
								<dealMonth>9</dealMonth>
								<dealYear>2026</dealYear>
								<dealingGbn>중개거래</dealingGbn>
								<estateAgentSggNm>서울 용산구</estateAgentSggNm>
								<excluUseAr>59.98</excluUseAr>
								<floor>12</floor>
								<jibun>810</jibun>
								<rgstDate>26.09.20</rgstDate>
								<sggCd>11170</sggCd>
								<umdNm>한남동</umdNm>
							</item>
						</items>
						<numOfRows>100</numOfRows>
						<pageNo>1</pageNo>
						<totalCount>1</totalCount>
					</body>
				</response>
				""",
				MediaType.APPLICATION_XML
			));

		List<ApartmentTradeApiItem> result =
			client.fetchAll("11170", "202609");

		assertThat(result).hasSize(1);

		ApartmentTradeApiItem item = result.get(0);

		assertThat(item.aptNm()).isEqualTo("한남테스트아파트");
		assertThat(item.umdNm()).isEqualTo("한남동");
		assertThat(item.sggCd()).isEqualTo("11170");
		assertThat(item.dealAmount()).isEqualTo("250,000");
		assertThat(item.excluUseAr()).isEqualTo("59.98");
		assertThat(item.dealYear()).isEqualTo("2026");
		assertThat(item.dealMonth()).isEqualTo("9");
		assertThat(item.dealDay()).isEqualTo("15");

		server.verify();
	}

	@Test
	void 조회_결과가_없으면_빈_목록을_반환한다() {
		server.expect(requestTo(
				"https://example.com/getRTMSDataSvcAptTrade"
					+ "?serviceKey=test-service-key"
					+ "&LAWD_CD=11170"
					+ "&DEAL_YMD=202609"
					+ "&pageNo=1"
					+ "&numOfRows=100"
			))
			.andExpect(method(HttpMethod.GET))
			.andRespond(withSuccess(
				"""
				<response>
					<header>
						<resultCode>000</resultCode>
						<resultMsg>OK</resultMsg>
					</header>
					<body>
						<items/>
						<numOfRows>100</numOfRows>
						<pageNo>1</pageNo>
						<totalCount>0</totalCount>
					</body>
				</response>
				""",
				MediaType.APPLICATION_XML
			));

		List<ApartmentTradeApiItem> result =
			client.fetchAll("11170", "202609");

		assertThat(result).isEmpty();

		server.verify();
	}

	@Test
	void API가_오류_코드를_반환하면_예외가_발생한다() {
		server.expect(requestTo(
				"https://example.com/getRTMSDataSvcAptTrade"
					+ "?serviceKey=test-service-key"
					+ "&LAWD_CD=11170"
					+ "&DEAL_YMD=202609"
					+ "&pageNo=1"
					+ "&numOfRows=100"
			))
			.andExpect(method(HttpMethod.GET))
			.andRespond(withSuccess(
				"""
				<response>
					<header>
						<resultCode>30</resultCode>
						<resultMsg>SERVICE_KEY_IS_NOT_REGISTERED_ERROR</resultMsg>
					</header>
				</response>
				""",
				MediaType.APPLICATION_XML
			));

		assertThatThrownBy(
			() -> client.fetchAll("11170", "202609")
		)
			.isInstanceOf(ApartmentTradeApiException.class)
			.hasMessageContaining("resultCode=30")
			.hasMessageContaining(
				"SERVICE_KEY_IS_NOT_REGISTERED_ERROR"
			);

		server.verify();
	}

	@Test
	void 잘못된_시군구_코드는_API_호출_전에_거부한다() {
		assertThatThrownBy(
			() -> client.fetchAll("1117", "202609")
		)
			.isInstanceOf(IllegalArgumentException.class)
			.hasMessageContaining("숫자 5자리");

		server.verify();
	}

	@Test
	void 존재하지_않는_계약월은_API_호출_전에_거부한다() {
		assertThatThrownBy(
			() -> client.fetchAll("11170", "202613")
		)
			.isInstanceOf(IllegalArgumentException.class)
			.hasMessageContaining("yyyyMM");

		server.verify();
	}

	@Test
	void 여러_페이지의_실거래가를_하나의_목록으로_합친다() {
		server.expect(requestTo(
				"https://example.com/getRTMSDataSvcAptTrade"
					+ "?serviceKey=test-service-key"
					+ "&LAWD_CD=11170"
					+ "&DEAL_YMD=202609"
					+ "&pageNo=1"
					+ "&numOfRows=100"
			))
			.andExpect(method(HttpMethod.GET))
			.andRespond(withSuccess(
				successResponse(
					"첫번째아파트",
					"150,000",
					1,
					2
				),
				MediaType.APPLICATION_XML
			));

		server.expect(requestTo(
				"https://example.com/getRTMSDataSvcAptTrade"
					+ "?serviceKey=test-service-key"
					+ "&LAWD_CD=11170"
					+ "&DEAL_YMD=202609"
					+ "&pageNo=2"
					+ "&numOfRows=100"
			))
			.andExpect(method(HttpMethod.GET))
			.andRespond(withSuccess(
				successResponse(
					"두번째아파트",
					"200,000",
					2,
					2
				),
				MediaType.APPLICATION_XML
			));

		List<ApartmentTradeApiItem> result =
			client.fetchAll("11170", "202609");

		assertThat(result)
			.extracting(ApartmentTradeApiItem::aptNm)
			.containsExactly("첫번째아파트", "두번째아파트");

		assertThat(result)
			.extracting(ApartmentTradeApiItem::dealAmount)
			.containsExactly("150,000", "200,000");

		server.verify();
	}

	private static String successResponse(
		String apartmentName,
		String dealAmount,
		int pageNo,
		int totalCount
	) {
		return """
		<response>
			<header>
				<resultCode>000</resultCode>
				<resultMsg>OK</resultMsg>
			</header>
			<body>
				<items>
					<item>
						<aptNm>%s</aptNm>
						<dealAmount>%s</dealAmount>
						<dealDay>15</dealDay>
						<dealMonth>9</dealMonth>
						<dealYear>2026</dealYear>
						<excluUseAr>59.98</excluUseAr>
						<jibun>810</jibun>
						<sggCd>11170</sggCd>
						<umdNm>한남동</umdNm>
					</item>
				</items>
				<numOfRows>100</numOfRows>
				<pageNo>%d</pageNo>
				<totalCount>%d</totalCount>
			</body>
		</response>
		""".formatted(
			apartmentName,
			dealAmount,
			pageNo,
			totalCount
		);
	}
}
