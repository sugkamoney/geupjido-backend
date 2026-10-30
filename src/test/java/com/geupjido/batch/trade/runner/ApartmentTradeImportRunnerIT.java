package com.geupjido.batch.trade.runner;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.UUID;

import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.condition.EnabledIfEnvironmentVariable;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.http.MediaType;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.web.client.MockRestServiceServer;
import org.springframework.web.client.RestClient;

import com.geupjido.batch.trade.client.ApartmentTradeApiClient;
import com.geupjido.batch.trade.config.ApartmentTradeApiProperties;
import com.geupjido.batch.trade.config.ApartmentTradeImportProperties;
import com.geupjido.batch.trade.mapper.ApartmentTradeMapper;
import com.geupjido.batch.trade.service.ApartmentTradeCollectionService;
import com.geupjido.batch.trade.service.ApartmentTradeScopeFilter;
import com.geupjido.batch.trade.service.ApartmentTradeBackfillPersistenceService;
import com.geupjido.batch.trade.service.ApartmentTradeImportService;
import com.geupjido.batch.trade.service.ApartmentTradePersistenceService;
import com.geupjido.trade.domain.ApartmentTrade;
import com.geupjido.trade.repository.ApartmentTradeRepository;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.springframework.test.web.client.match.MockRestRequestMatchers.requestTo;
import static org.springframework.test.web.client.response.MockRestResponseCreators.withSuccess;

@EnabledIfEnvironmentVariable(named = "RUN_POSTGIS_IT", matches = "true")
@SpringBootTest
@ActiveProfiles("local")
class ApartmentTradeImportRunnerIT {

	private static final String REQUEST_URL =
		"https://example.com/getRTMSDataSvcAptTrade"
			+ "?serviceKey=test-service-key"
			+ "&LAWD_CD=11170"
			+ "&DEAL_YMD=202609"
			+ "&pageNo=1"
			+ "&numOfRows=100";

	private final String testPrefix =
		"IT80-" + UUID.randomUUID().toString().substring(0, 8);

	@Autowired
	private ApartmentTradePersistenceService persistenceService;

	@Autowired
	private ApartmentTradeBackfillPersistenceService backfillPersistenceService;

	@Autowired
	private ApartmentTradeRepository repository;

	@Autowired
	private ApartmentTradeScopeFilter scopeFilter;

	@Autowired
	private JdbcTemplate jdbcTemplate;

	private MockRestServiceServer server;
	private ApartmentTradeImportRunner runner;

	@BeforeEach
	void setUp() {
		RestClient.Builder builder = RestClient.builder()
			.baseUrl("https://example.com");
		server = MockRestServiceServer.bindTo(builder).build();

		ApartmentTradeApiClient client = new ApartmentTradeApiClient(
			builder.build(),
			new ApartmentTradeApiProperties(
				true, "https://example.com", "test-service-key"
			)
		);
		ApartmentTradeCollectionService collectionService =
			new ApartmentTradeCollectionService(
				client, new ApartmentTradeMapper(), scopeFilter
			);
		ApartmentTradeImportService importService =
			new ApartmentTradeImportService(
				collectionService, persistenceService,
				backfillPersistenceService
			);
		runner = new ApartmentTradeImportRunner(
			new ApartmentTradeImportProperties(true, "11170", "202609"),
			importService
		);
	}

	@AfterEach
	void deleteTestTrades() {
		jdbcTemplate.update(
			"DELETE FROM apartment_trade WHERE raw_apartment_name LIKE ?",
			testPrefix + "%"
		);
	}

	@Test
	void API_응답을_DB에_저장하고_재실행하면_중복되지_않는다() {
		String name = testPrefix + "-A";
		expectResponse(item(name, null), 1);
		expectResponse(item(name, null), 1);

		runner.run(null);
		assertThat(count(name)).isEqualTo(1);
		assertThat(find(name).isCanceled()).isFalse();

		runner.run(null);
		assertThat(count(name)).isEqualTo(1);
		server.verify();
	}

	@Test
	void 해제_응답은_기존_행을_갱신하고_충돌한_월은_롤백한다() {
		String existingName = testPrefix + "-A";
		String newName = testPrefix + "-B";
		expectResponse(item(existingName, null), 1);
		expectResponse(item(existingName, "26.09.20"), 1);
		expectResponse(
			item(newName, null)
				+ item(existingName, "26.09.21"),
			2
		);

		runner.run(null);
		runner.run(null);
		assertThat(find(existingName).isCanceled()).isTrue();
		assertThat(find(existingName).getCancellationDate())
			.isEqualTo(LocalDate.of(2026, 9, 20));

		assertThatThrownBy(() -> runner.run(null))
			.isInstanceOf(IllegalStateException.class)
			.hasMessageContaining("해제일");

		assertThat(count(newName)).isZero();
		assertThat(find(existingName).getCancellationDate())
			.isEqualTo(LocalDate.of(2026, 9, 20));
		server.verify();
	}

	private void expectResponse(String items, int totalCount) {
		server.expect(requestTo(REQUEST_URL))
			.andRespond(withSuccess(
				"""
				<response>
					<header><resultCode>000</resultCode><resultMsg>OK</resultMsg></header>
					<body>
						<items>%s</items>
						<numOfRows>100</numOfRows><pageNo>1</pageNo>
						<totalCount>%d</totalCount>
					</body>
				</response>
				""".formatted(items, totalCount),
				MediaType.APPLICATION_XML
			));
	}

	private static String item(String name, String cancellationDate) {
		String cancellation = cancellationDate == null
			? ""
			: "<cdealType>O</cdealType><cdealDay>"
				+ cancellationDate + "</cdealDay>";
		return """
			<item>
				<aptNm>%s</aptNm>
				<dealAmount>250,000</dealAmount>
				<dealDay>15</dealDay><dealMonth>9</dealMonth><dealYear>2026</dealYear>
				<excluUseAr>59.98</excluUseAr><floor>12</floor>
				<jibun>810</jibun><sggCd>11170</sggCd><umdNm>한남동</umdNm>
				%s
			</item>
			""".formatted(name, cancellation);
	}

	private ApartmentTrade find(String name) {
		return repository.findBySourceKey(
			"11170", "한남동", name, LocalDate.of(2026, 9, 15),
			new BigDecimal("59.98"), (short) 12, 250_000L
		).orElseThrow();
	}

	private long count(String name) {
		return jdbcTemplate.queryForObject(
			"SELECT COUNT(*) FROM apartment_trade WHERE raw_apartment_name = ?",
			Long.class, name
		);
	}
}
