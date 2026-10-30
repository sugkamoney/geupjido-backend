package com.geupjido.trade.repository;

import java.math.BigDecimal;
import java.util.List;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.condition.EnabledIfEnvironmentVariable;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.transaction.annotation.Transactional;

import static org.assertj.core.api.Assertions.assertThat;

@EnabledIfEnvironmentVariable(named = "RUN_POSTGIS_IT", matches = "true")
@SpringBootTest
@ActiveProfiles("local")
@Transactional
class RepresentativeTradeCandidateRepositoryIT {

	@Autowired
	private JdbcTemplate jdbcTemplate;

	@Autowired
	private ApartmentTradeRepository tradeRepository;

	@Autowired
	private ApartmentPresaleTradeRepository presaleTradeRepository;

	@BeforeEach
	void setUp() {
		jdbcTemplate.update("""
			INSERT INTO region (code, name, is_active, display_order)
			VALUES ('test-rep-price', '테스트', true, 999)
			""");
		jdbcTemplate.update("""
			INSERT INTO city (id, region_code, name, polygon, center)
			VALUES (
				'TPRC0001', 'test-rep-price', '테스트시',
				ST_GeomFromText('MULTIPOLYGON(((0 0, 1 0, 1 1, 0 1, 0 0)))', 4326),
				ST_SetSRID(ST_MakePoint(0.5, 0.5), 4326)
			)
			""");
		for (String zoneId : List.of("test-rep-price-one", "test-rep-price-two")) {
			jdbcTemplate.update("""
				INSERT INTO zone (id, city_id, name, dong_codes, polygon, center)
				VALUES (
					?, 'TPRC0001', ?, ARRAY['0000000000'],
					ST_GeomFromText('MULTIPOLYGON(((0 0, 1 0, 1 1, 0 1, 0 0)))', 4326),
					ST_SetSRID(ST_MakePoint(0.5, 0.5), 4326)
				)
				""", zoneId, zoneId);
		}
		jdbcTemplate.update("""
			INSERT INTO complex (
				id, zone_id, name, address_jibun, legal_dong_code,
				households, is_active
			)
			VALUES (
				'TPRC00001', 'test-rep-price-one', '테스트 단지',
				'테스트 주소', '0000000000', 100, false
			)
			""");
	}

	@Test
	void 연결_권역과_정상_중개거래_근접_면적을_모두_만족한_거래만_조회한다() {
		long older = insertTrade("2026-09-01", 100, "58.000000",
			"중개거래", false, "test-rep-price-one");
		long newest = insertTrade("2026-10-01", 200, "60.999999",
			"중개거래", false, "test-rep-price-one");
		insertTrade("2026-10-02", 300, "59.500000",
			"직거래", false, "test-rep-price-one");
		insertTrade("2026-10-03", 400, "59.500000",
			"중개거래", true, "test-rep-price-one");
		insertTrade("2026-10-04", 500, "61.000000",
			"중개거래", false, "test-rep-price-one");
		insertTrade("2026-10-04", 500, "57.999999",
			"중개거래", false, "test-rep-price-one");
		insertTrade("2026-10-05", 600, "59.500000",
			"중개거래", false, "test-rep-price-two");

		var candidates = tradeRepository.findRepresentativeCandidates(
			List.of("TPRC00001"), BigDecimal.valueOf(58),
			BigDecimal.valueOf(61)
		);

		assertThat(candidates).extracting(trade -> trade.getId())
			.containsExactly(newest, older);
		assertThat(candidates.getFirst().getExclusiveArea())
			.isEqualByComparingTo("60.999999");
	}

	@Test
	void 보류된_기존_거래는_대표_가격_후보에서_제외한다() {
		long disputed = insertTrade("2026-10-01", 200, "59.000000",
			"중개거래", false, "test-rep-price-one");
		long safe = insertTrade("2026-09-01", 100, "59.000000",
			"중개거래", false, "test-rep-price-one");
		jdbcTemplate.update("""
			INSERT INTO apartment_trade_conflict (
				district_code, deal_year_month, source_ordinal,
				conflict_reason, existing_trade_id, legal_dong_name,
				raw_apartment_name, deal_date, deal_amount, exclusive_area,
				is_canceled
			) VALUES (
				'11680', '202610', 0, 'SOURCE_STATUS_CONFLICT', ?,
				'역삼동', '테스트 단지', '2026-10-01', 200,
				59.000000, false
			)
			""", disputed);

		var candidates = tradeRepository.findRepresentativeCandidates(
			List.of("TPRC00001"), BigDecimal.valueOf(58),
			BigDecimal.valueOf(61)
		);

		assertThat(candidates).extracting(trade -> trade.getId())
			.containsExactly(safe);
	}

	@Test
	void 확대_범위는_44와_65를_포함하고_범위_밖과_비정상_거래를_제외한다() {
		long low = insertTrade("2026-10-01", 100, "44.000000",
			"중개거래", false, "test-rep-price-one");
		long high = insertTrade("2026-10-02", 200, "65.000000",
			"중개거래", false, "test-rep-price-one");
		insertTrade("2026-10-03", 300, "43.999999",
			"중개거래", false, "test-rep-price-one");
		insertTrade("2026-10-04", 400, "65.000001",
			"중개거래", false, "test-rep-price-one");
		insertTrade("2026-10-05", 500, "59.000000",
			"직거래", false, "test-rep-price-one");
		insertTrade("2026-10-06", 600, "59.000000",
			"중개거래", true, "test-rep-price-one");

		var candidates = tradeRepository.findFallbackRepresentativeCandidates(
			List.of("TPRC00001"), BigDecimal.valueOf(44),
			BigDecimal.valueOf(65)
		);

		assertThat(candidates).extracting(trade -> trade.getId())
			.containsExactly(high, low);
	}

	@Test
	void 직거래_후보도_면적_권역_해제_충돌_조건을_검증한다() {
		long primary = insertTrade("2026-10-01", 200, "59.000000",
			"직거래", false, "test-rep-price-one");
		long upper = insertTrade("2026-10-02", 300, "65.000000",
			"직거래", false, "test-rep-price-one");
		insertTrade("2026-10-03", 400, "59.000000",
			"직거래", true, "test-rep-price-one");
		insertTrade("2026-10-04", 500, "59.000000",
			"직거래", false, "test-rep-price-two");
		insertTrade("2026-10-05", 600, "59.000000",
			"중개거래", false, "test-rep-price-one");
		long disputed = insertTrade("2026-10-06", 700, "59.000000",
			"직거래", false, "test-rep-price-one");
		jdbcTemplate.update("""
			INSERT INTO apartment_trade_conflict (
				district_code, deal_year_month, source_ordinal,
				conflict_reason, existing_trade_id, legal_dong_name,
				raw_apartment_name, deal_date, deal_amount, exclusive_area,
				is_canceled
			) VALUES (
				'11680', '202610', 1, 'SOURCE_STATUS_CONFLICT', ?,
				'역삼동', '테스트 단지', '2026-10-06', 700,
				59.000000, false
			)
			""", disputed);

		assertThat(tradeRepository.findDirectRepresentativeCandidates(
			List.of("TPRC00001"), BigDecimal.valueOf(58),
			BigDecimal.valueOf(61), false
		)).extracting(trade -> trade.getId()).containsExactly(primary);
		assertThat(tradeRepository.findDirectRepresentativeCandidates(
			List.of("TPRC00001"), BigDecimal.valueOf(44),
			BigDecimal.valueOf(65), true
		)).extracting(trade -> trade.getId()).containsExactly(upper, primary);
	}

	@Test
	void 다른_면적_후보는_59제곱에_가까운_순서로_조회한다() {
		long near = insertTrade("2026-09-01", 200, "68.000000",
			"중개거래", false, "test-rep-price-one");
		long small = insertTrade("2026-10-01", 100, "35.000000",
			"중개거래", false, "test-rep-price-one");
		long large = insertTrade("2026-10-02", 1_430_000,
			"206.895300", "중개거래", false,
			"test-rep-price-one");
		insertTrade("2026-10-03", 300, "59.000000",
			"중개거래", false, "test-rep-price-one");
		insertTrade("2026-10-04", 400, "67.000000",
			"중개거래", true, "test-rep-price-one");
		insertTrade("2026-10-05", 500, "66.000000",
			"중개거래", false, "test-rep-price-two");
		insertTrade("2026-10-06", 600, "66.000000",
			"직거래", false, "test-rep-price-one");

		assertThat(tradeRepository.findOtherAreaRepresentativeCandidates(
			List.of("TPRC00001"), "중개거래"
		)).extracting(trade -> trade.getId())
			.containsExactly(near, small, large);
	}

	@Test
	void 권리_거래는_해제_동일거래와_권역_불일치를_제외한다() {
		insertPresale(1, "2026-02-12", 670_000, "84.746700",
			false, "test-rep-price-one");
		insertPresale(2, "2026-02-13", 680_000, "84.746700",
			false, "test-rep-price-one");
		insertPresale(3, "2026-02-13", 680_000, "84.746700",
			true, "test-rep-price-one");
		insertPresale(4, "2026-02-14", 700_000, "59.000000",
			false, "test-rep-price-two");

		var candidates = presaleTradeRepository
			.findRepresentativeCandidates(List.of("TPRC00001"));

		assertThat(candidates).extracting(candidate -> candidate.amount())
			.containsExactly(670_000L);
	}

	private void insertPresale(
		int ordinal, String date, long amount, String area,
		boolean canceled, String zoneId
	) {
		jdbcTemplate.update("""
			INSERT INTO apartment_presale_trade (
				complex_id, zone_id, district_code, deal_year_month,
				source_ordinal, deal_date, deal_amount, exclusive_area,
				floor, legal_dong_name, raw_apartment_name,
				is_canceled, cancellation_date
			) VALUES (
				'TPRC00001', ?, '11680', '202602', ?, ?::date, ?,
				?::numeric, 1, '역삼동', '테스트 단지', ?,
				CASE WHEN ? THEN ?::date ELSE NULL END
			)
			""", zoneId, ordinal, date, amount, area, canceled,
			canceled, date);
	}

	private long insertTrade(
		String date,
		long amount,
		String area,
		String dealingType,
		boolean canceled,
		String zoneId
	) {
		return jdbcTemplate.queryForObject("""
			INSERT INTO apartment_trade (
				complex_id, zone_id, deal_date, deal_amount, exclusive_area,
				district_code, legal_dong_name, raw_apartment_name,
				dealing_type, is_canceled, cancellation_date
			)
			VALUES (
				'TPRC00001', ?, ?::date, ?, ?::numeric,
				'11680', '역삼동', '테스트 단지', ?, ?,
				CASE WHEN ? THEN ?::date ELSE NULL END
			)
			RETURNING id
			""", Long.class, zoneId, date, amount, area,
			dealingType, canceled, canceled, date);
	}
}
