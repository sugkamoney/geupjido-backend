package com.geupjido.batch.trade.service;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.List;

import org.junit.jupiter.api.Test;
import org.locationtech.jts.geom.Coordinate;
import org.locationtech.jts.geom.GeometryFactory;
import org.locationtech.jts.geom.PrecisionModel;
import org.springframework.data.domain.Sort;
import org.springframework.test.util.ReflectionTestUtils;

import com.geupjido.complex.domain.Complex;
import com.geupjido.complex.domain.RepresentativePriceSource;
import com.geupjido.complex.repository.ComplexRepository;
import com.geupjido.trade.domain.ApartmentTrade;
import com.geupjido.trade.repository.ApartmentTradeRepository;
import com.geupjido.trade.repository.ApartmentPresaleTradeRepository;
import com.geupjido.trade.repository.ApartmentPresaleTradeRepository.Candidate;
import com.geupjido.zone.domain.Zone;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

class ComplexRepresentativePriceServiceTest {

	private final ComplexRepository complexRepository =
		mock(ComplexRepository.class);
	private final ApartmentTradeRepository tradeRepository =
		mock(ApartmentTradeRepository.class);
	private final ApartmentPresaleTradeRepository presaleTradeRepository =
		mock(ApartmentPresaleTradeRepository.class);
	private final ComplexRepresentativePriceService service =
		new ComplexRepresentativePriceService(
			complexRepository, tradeRepository, presaleTradeRepository
		);

	@Test
	void 최신일_짝수_후보는_상위_중앙가격을_쓰고_동액이면_작은_ID의_실제_면적을_택한다() {
		Complex first = complex("A0001", "zone-one");
		Complex second = complex("A0002", "zone-one");
		Complex third = complex("A0003", "zone-one");
		Complex noTrade = complex("A0004", "zone-one");
		Complex otherZone = complex("A0005", "zone-two");
		List<Complex> complexes = List.of(
			first, second, third, noTrade, otherZone
		);
		LocalDate latest = LocalDate.of(2026, 10, 20);
		when(complexRepository.findAll(Sort.by("id")))
			.thenReturn(complexes);
		when(tradeRepository.findRepresentativeCandidates(
			List.of("A0001", "A0002", "A0003", "A0004", "A0005"),
			BigDecimal.valueOf(58), BigDecimal.valueOf(61)
		)).thenReturn(List.of(
			trade(1, first, latest, 100, "59.100000"),
			trade(10, first, latest, 200, "59.123456"),
			trade(20, first, latest, 200, "59.987654"),
			trade(30, first, latest, 300, "59.200000"),
			trade(40, first, latest.minusDays(1), 500, "59.300000"),
			trade(50, second, latest.plusDays(1), 200, "58.400000"),
			trade(60, third, latest, 300, "59.500000"),
			trade(70, otherZone, latest, 200, "60.600000")
		));

		var result = service.recalculateAll();

		assertThat(first.getPrice()).isEqualTo(200);
		assertThat(first.getPriceSource())
			.isEqualTo(RepresentativePriceSource.APARTMENT_SALE);
		assertThat(first.getRepresentativeArea())
			.isEqualByComparingTo("59.123456");
		assertThat(first.getLastDealDate()).isEqualTo(latest);
		assertThat(third.getZoneRank()).isEqualTo(1);
		assertThat(first.getZoneRank()).isEqualTo(3);
		assertThat(second.getZoneRank()).isEqualTo(2);
		assertThat(second.getRepresentativeArea())
			.isEqualByComparingTo("58.400000");
		assertThat(otherZone.getZoneRank()).isEqualTo(1);
		assertThat(otherZone.getRepresentativeArea())
			.isEqualByComparingTo("60.600000");
		assertThat(noTrade.getPrice()).isNull();
		assertThat(noTrade.getPriceSource()).isNull();
		assertThat(noTrade.getRepresentativeArea()).isNull();
		assertThat(noTrade.getLastDealDate()).isNull();
		assertThat(noTrade.getZoneRank()).isNull();
		assertThat(result.totalComplexCount()).isEqualTo(5);
		assertThat(result.pricedComplexCount()).isEqualTo(4);
		assertThat(result.rankedComplexCount()).isEqualTo(4);
		verify(tradeRepository).findFallbackRepresentativeCandidates(
			List.of("A0004"), BigDecimal.valueOf(44), BigDecimal.valueOf(65)
		);
	}

	@Test
	void 일반_매매가_없는_단지만_권리_거래를_임시_대표값으로_선택한다() {
		Complex ordinary = complex("A0001", "zone-one");
		Complex rights = complex("A0002", "zone-one");
		LocalDate date = LocalDate.of(2026, 2, 12);
		when(complexRepository.findAll(Sort.by("id")))
			.thenReturn(List.of(ordinary, rights));
		when(tradeRepository.findRepresentativeCandidates(
			List.of("A0001", "A0002"),
			BigDecimal.valueOf(58), BigDecimal.valueOf(61)
		)).thenReturn(List.of(trade(1, ordinary, date, 200_000,
			"59.000000")));
		when(presaleTradeRepository.findRepresentativeCandidates(
			List.of("A0002")
		)).thenReturn(List.of(
			new Candidate(11, "A0002", date, 670_000,
				new BigDecimal("84.746700")),
			new Candidate(12, "A0002", date.plusDays(2), 830_000,
				new BigDecimal("111.768300"))
		));

		var result = service.recalculateAll();

		assertThat(ordinary.getPriceSource())
			.isEqualTo(RepresentativePriceSource.APARTMENT_SALE);
		assertThat(rights.getPrice()).isEqualTo(670_000);
		assertThat(rights.getRepresentativeArea())
			.isEqualByComparingTo("84.746700");
		assertThat(rights.getLastDealDate()).isEqualTo(date);
		assertThat(rights.getPriceSource())
			.isEqualTo(RepresentativePriceSource.PRESALE_OR_OCCUPANCY_RIGHT);
		assertThat(rights.getZoneRank()).isEqualTo(1);
		assertThat(result.pricedComplexCount()).isEqualTo(2);
	}

	@Test
	void 근접_면적_거래가_없는_단지만_확대_범위로_선택한다() {
		Complex primary = complex("A0001", "zone-one");
		Complex fallback = complex("A0002", "zone-one");
		LocalDate date = LocalDate.of(2026, 10, 20);
		when(complexRepository.findAll(Sort.by("id")))
			.thenReturn(List.of(primary, fallback));
		when(tradeRepository.findRepresentativeCandidates(
			List.of("A0001", "A0002"),
			BigDecimal.valueOf(58), BigDecimal.valueOf(61)
		)).thenReturn(List.of(trade(1, primary, date.minusDays(2), 200,
			"59.000000")));
		when(tradeRepository.findFallbackRepresentativeCandidates(
			List.of("A0002"), BigDecimal.valueOf(44),
			BigDecimal.valueOf(65)
		)).thenReturn(List.of(trade(2, fallback, date, 300,
			"65.000000")));

		var result = service.recalculateAll();

		assertThat(primary.getPrice()).isEqualTo(200);
		assertThat(primary.getRepresentativeArea())
			.isEqualByComparingTo("59.000000");
		assertThat(fallback.getPrice()).isEqualTo(300);
		assertThat(fallback.getRepresentativeArea())
			.isEqualByComparingTo("65.000000");
		assertThat(fallback.getZoneRank()).isEqualTo(1);
		assertThat(primary.getZoneRank()).isEqualTo(2);
		assertThat(result.pricedComplexCount()).isEqualTo(2);
	}

	@Test
	void 중개거래가_없을_때만_직거래를_근접_면적부터_선택한다() {
		Complex brokerFallback = complex("A0001", "zone-one");
		Complex directPrimary = complex("A0002", "zone-one");
		Complex directFallback = complex("A0003", "zone-one");
		LocalDate date = LocalDate.of(2026, 10, 20);
		when(complexRepository.findAll(Sort.by("id")))
			.thenReturn(List.of(brokerFallback, directPrimary,
				directFallback));
		when(tradeRepository.findFallbackRepresentativeCandidates(
			List.of("A0001", "A0002", "A0003"),
			BigDecimal.valueOf(44), BigDecimal.valueOf(65)
		)).thenReturn(List.of(trade(1, brokerFallback,
			date.minusDays(10), 300, "50.000000")));
		when(tradeRepository.findDirectRepresentativeCandidates(
			List.of("A0002", "A0003"), BigDecimal.valueOf(58),
			BigDecimal.valueOf(61), false
		)).thenReturn(List.of(trade(2, directPrimary,
			date.minusDays(5), 200, "59.000000")));
		when(tradeRepository.findDirectRepresentativeCandidates(
			List.of("A0003"), BigDecimal.valueOf(44),
			BigDecimal.valueOf(65), true
		)).thenReturn(List.of(trade(3, directFallback,
			date, 100, "65.000000")));

		var result = service.recalculateAll();

		assertThat(brokerFallback.getPrice()).isEqualTo(300);
		assertThat(directPrimary.getPrice()).isEqualTo(200);
		assertThat(directPrimary.getRepresentativeArea())
			.isEqualByComparingTo("59.000000");
		assertThat(directFallback.getPrice()).isEqualTo(100);
		assertThat(directFallback.getRepresentativeArea())
			.isEqualByComparingTo("65.000000");
		assertThat(result.pricedComplexCount()).isEqualTo(3);
	}

	@Test
	void 대형_평형도_실제_거래를_선택하고_면적당_가격으로_순위를_매긴다() {
		Complex large = complex("A0001", "zone-one");
		Complex small = complex("A0002", "zone-one");
		Complex sameUnitPrice = complex("A0003", "zone-one");
		LocalDate date = LocalDate.of(2026, 10, 20);
		when(complexRepository.findAll(Sort.by("id")))
			.thenReturn(List.of(large, small, sameUnitPrice));
		when(tradeRepository.findOtherAreaRepresentativeCandidates(
			List.of("A0001", "A0002", "A0003"), "중개거래"
		)).thenReturn(List.of(
			trade(1, large, date.minusDays(1), 1_430_000,
				"206.895300"),
			trade(2, large, date, 2_620_000, "273.942400"),
			trade(3, small, date, 100_000, "40.000000"),
			trade(4, sameUnitPrice, date, 200_000,
				"80.000000")
		));

		var result = service.recalculateAll();

		assertThat(large.getPrice()).isEqualTo(1_430_000);
		assertThat(large.getRepresentativeArea())
			.isEqualByComparingTo("206.895300");
		assertThat(large.getLastDealDate()).isEqualTo(date.minusDays(1));
		assertThat(large.getZoneRank()).isEqualTo(1);
		assertThat(small.getZoneRank()).isEqualTo(2);
		assertThat(sameUnitPrice.getZoneRank()).isEqualTo(2);
		assertThat(result.pricedComplexCount()).isEqualTo(3);
	}

	private static Complex complex(String id, String zoneId) {
		Complex complex = new Complex(
			id, "테스트 단지", "서울시 테스트동 1", null,
			"1168010100", 100, null, null
		);
		var factory = new GeometryFactory(new PrecisionModel(), 4326);
		complex.updateLocation(factory.createPoint(
			new Coordinate(127.0, 37.5)
		));
		Zone zone = mock(Zone.class);
		when(zone.getId()).thenReturn(zoneId);
		complex.assignZone(zone);
		return complex;
	}

	private static ApartmentTrade trade(
		long id,
		Complex complex,
		LocalDate date,
		long amount,
		String area
	) {
		ApartmentTrade trade = new ApartmentTrade(
			date, amount, new BigDecimal(area), (short) 1,
			(short) 2020, "11680", "역삼동", "테스트 단지", "1",
			null, "중개거래", false, null
		);
		ReflectionTestUtils.setField(trade, "id", id);
		trade.linkToComplex(complex);
		return trade;
	}
}
