package com.geupjido.batch.trade.service;

import java.math.BigDecimal;
import java.nio.file.Path;
import java.time.LocalDate;
import java.util.List;
import java.util.Map;

import org.junit.jupiter.api.Test;
import org.locationtech.jts.geom.Coordinate;
import org.locationtech.jts.geom.GeometryFactory;
import org.springframework.transaction.TransactionStatus;
import org.springframework.transaction.support.TransactionTemplate;
import org.springframework.test.util.ReflectionTestUtils;

import com.geupjido.batch.location.model.LegalDongCodeReference;
import com.geupjido.batch.location.model.LegalDongCodeReference.Key;
import com.geupjido.batch.location.model.LocationMapping;
import com.geupjido.batch.location.reader.LegalDongCodeReferenceReader;
import com.geupjido.batch.location.reader.LocationMappingReader;
import com.geupjido.batch.trade.model.ApartmentTradeCandidateAssessment.Status;
import com.geupjido.complex.domain.Complex;
import com.geupjido.complex.repository.ComplexRepository;
import com.geupjido.trade.domain.ApartmentTrade;
import com.geupjido.trade.repository.ApartmentTradeRepository;
import com.geupjido.zone.domain.Zone;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.doAnswer;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

class ApartmentTradeExactLinkServiceTest {

	@Test
	void 연도와_법정동_기본권역이_달라도_검증된_단지에_연결한다() {
		var referenceReader = mock(LegalDongCodeReferenceReader.class);
		var mappingReader = mock(LocationMappingReader.class);
		var tradeRepository = mock(ApartmentTradeRepository.class);
		var complexRepository = mock(ComplexRepository.class);
		var transactionTemplate = mock(TransactionTemplate.class);
		Path referencePath = Path.of("reference.tsv");
		Path mappingPath = Path.of("mapping.json");
		when(referenceReader.read(referencePath)).thenReturn(
			new LegalDongCodeReference(Map.of(
				new Key("11170", "이촌동"), "1117012900"
			))
		);
		when(mappingReader.read(mappingPath)).thenReturn(new LocationMapping(
			List.of(new LocationMapping.RegionDefinition(
				"seoul", "서울", true, 1
			)),
			List.of(new LocationMapping.CityDefinition(
				"11170", "seoul", "용산구"
			)),
			List.of(new LocationMapping.ZoneDefinition(
				"yongsan-ichon", "11170", "이촌",
				List.of("1117012900"), new BigDecimal("2.0")
			))
		));
		ApartmentTrade matching = trade("한가람", "404");
		ApartmentTrade another = trade("다른단지", "405");
		ApartmentTrade verifiedKey = trade("수동별칭", "404");
		ReflectionTestUtils.setField(matching, "id", 1L);
		ReflectionTestUtils.setField(another, "id", 2L);
		ReflectionTestUtils.setField(verifiedKey, "id", 3L);
		when(tradeRepository
			.findByIdGreaterThanAndComplexIsNullAndZoneIsNullOrderByIdAsc(
				any(), any()
			))
			.thenReturn(List.of(matching, another, verifiedKey), List.of());
		when(tradeRepository.findVerifiedSourceMappings())
			.thenReturn(List.<Object[]>of(new Object[] {
				"11170", "이촌동", "404", "수동별칭", (short)1998,
				"A", "yongsan-ichon-west"
			}));
		Complex matchingComplex = complex("A", "한가람", "404",
			"yongsan-ichon-west");
		matchingComplex.updateSaleType("분양");
		Complex otherSameLot = complex("C", "다른한가람", "404",
			"yongsan-ichon-west");
		Complex anotherComplex = complex("B", "다른단지", "405",
			"yongsan-ichon");
		when(complexRepository.findByLegalDongCodeIn(any()))
			.thenReturn(List.of(otherSameLot, matchingComplex,
				anotherComplex));
		doAnswer(invocation -> invocation.getArgument(0,
			org.springframework.transaction.support.TransactionCallback.class)
			.doInTransaction(mock(TransactionStatus.class)))
			.when(transactionTemplate).execute(any());

		var candidateFinder = new ApartmentTradeCandidateFinder(complexRepository);
		var assessmentService = new ApartmentTradeCandidateAssessmentService(
			candidateFinder
		);
		var service = new ApartmentTradeExactLinkService(
			referenceReader, mappingReader, tradeRepository, complexRepository,
			candidateFinder, assessmentService, transactionTemplate
		);
		var result = service.linkExact(referencePath, mappingPath);

		assertThat(result.scanned()).isEqualTo(3);
		assertThat(result.linked()).isEqualTo(3);
		assertThat(result.zoneMismatch()).isZero();
		assertThat(result.assessments()).containsEntry(Status.NAME_EXACT, 2L);
		assertThat(result.assessments()).containsEntry(
			Status.VERIFIED_SOURCE_KEY, 1L);
		assertThat(matching.getComplex()).isEqualTo(matchingComplex);
		assertThat(matching.getZone().getId()).isEqualTo("yongsan-ichon-west");
		assertThat(another.getComplex()).isEqualTo(anotherComplex);
		assertThat(verifiedKey.getComplex()).isEqualTo(matchingComplex);
	}

	private static ApartmentTrade trade(String name, String lot) {
		return new ApartmentTrade(
			LocalDate.of(2026, 9, 1), 100_000L,
			new BigDecimal("59.8"), (short) 1, (short) 1998,
			"11170", "이촌동", name, lot, null, "중개거래",
			false, null
		);
	}

	private static Complex complex(
		String id, String name, String lot, String zoneId
	) {
		Complex complex = new Complex(
			id, name, "서울특별시 용산구 이촌동 " + lot,
			null, "1117012900", 100, null,
			LocalDate.of(1997, 1, 1)
		);
		var point = new GeometryFactory().createPoint(
			new Coordinate(127, 37)
		);
		point.setSRID(4326);
		complex.updateLocation(point);
		Zone zone = mock(Zone.class);
		when(zone.getId()).thenReturn(zoneId);
		complex.assignZone(zone);
		return complex;
	}
}
