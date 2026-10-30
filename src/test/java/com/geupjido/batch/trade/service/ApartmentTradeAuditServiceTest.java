package com.geupjido.batch.trade.service;

import java.io.StringWriter;
import java.math.BigDecimal;
import java.nio.file.Path;
import java.time.LocalDate;
import java.util.List;
import java.util.Map;

import org.junit.jupiter.api.Test;
import org.locationtech.jts.geom.Coordinate;
import org.locationtech.jts.geom.GeometryFactory;
import org.springframework.data.domain.PageImpl;
import org.springframework.data.domain.Pageable;

import com.geupjido.batch.location.model.LegalDongCodeReference;
import com.geupjido.batch.location.model.LegalDongCodeReference.Key;
import com.geupjido.batch.location.model.LocationMapping;
import com.geupjido.batch.location.reader.LegalDongCodeReferenceReader;
import com.geupjido.batch.location.reader.LocationMappingReader;
import com.geupjido.complex.domain.Complex;
import com.geupjido.trade.domain.ApartmentTrade;
import com.geupjido.trade.repository.ApartmentTradeRepository;
import com.geupjido.zone.domain.Zone;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

class ApartmentTradeAuditServiceTest {

	@Test
	void 분할_권역의_거래는_단지의_실제_권역과_일치하면_정상_연결이다()
		throws Exception {
		Path referencePath = Path.of("reference.tsv");
		Path mappingPath = Path.of("mapping.json");
		var referenceReader = mock(LegalDongCodeReferenceReader.class);
		var mappingReader = mock(LocationMappingReader.class);
		var tradeRepository = mock(ApartmentTradeRepository.class);
		var assessmentService = mock(
			ApartmentTradeCandidateAssessmentService.class);
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
				"yongsan-ichon", "11170", "동부이촌",
				List.of("1117012900"), new BigDecimal("2.0")
			))
		));

		Complex complex = new Complex("A14003006", "동아그린",
			"서울특별시 용산구 이촌동 404", null, "1117012900",
			100, null, LocalDate.of(1998, 1, 1));
		var point = new GeometryFactory().createPoint(
			new Coordinate(126.97, 37.52));
		point.setSRID(4326);
		complex.updateLocation(point);
		Zone westZone = mock(Zone.class);
		when(westZone.getId()).thenReturn("yongsan-ichon-west");
		complex.assignZone(westZone);
		ApartmentTrade trade = new ApartmentTrade(
			LocalDate.of(2026, 9, 1), 100_000L, new BigDecimal("59.8"),
			(short)1, (short)1998, "11170", "이촌동", "동아그린",
			"404", null, "중개거래", false, null);
		trade.linkToComplex(complex);
		when(tradeRepository.findAll(any(Pageable.class)))
			.thenReturn(new PageImpl<>(List.of(trade)));

		StringWriter output = new StringWriter();
		var service = new ApartmentTradeAuditService(referenceReader,
			mappingReader, tradeRepository, assessmentService);
		var result = service.auditAll(referencePath, mappingPath, output);

		assertThat(result.linkedCount()).isEqualTo(1);
		assertThat(result.inconsistentLinkCount()).isZero();
		assertThat(output.toString()).contains("\tLINKED\t");
		assertThat(output.toString()).contains("yongsan-ichon-west");
	}
}
