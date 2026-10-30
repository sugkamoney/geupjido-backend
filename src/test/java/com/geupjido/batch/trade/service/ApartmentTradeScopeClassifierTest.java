package com.geupjido.batch.trade.service;

import java.math.BigDecimal;
import java.nio.file.Path;
import java.util.List;

import org.junit.jupiter.api.Test;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.geupjido.batch.location.exception.LocationMappingException;
import com.geupjido.batch.location.model.LegalDongCodeReference;
import com.geupjido.batch.location.model.LocationMapping;
import com.geupjido.batch.location.model.LocationMapping.CityDefinition;
import com.geupjido.batch.location.model.LocationMapping.RegionDefinition;
import com.geupjido.batch.location.model.LocationMapping.ZoneDefinition;
import com.geupjido.batch.location.reader.LegalDongCodeReferenceReader;
import com.geupjido.batch.location.reader.LocationMappingReader;
import com.geupjido.batch.trade.model.ApartmentTradeScope;
import com.geupjido.batch.trade.model.ApartmentTradeScope.Status;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

class ApartmentTradeScopeClassifierTest {

	private final LegalDongCodeReference reference =
		new LegalDongCodeReferenceReader().read(Path.of(
			"data/location/reference/legal-dongs-20260917.tsv"
		));

	private final LocationMapping mapping = new LocationMappingReader(
		new ObjectMapper()
	).read(Path.of("data/location/mapping/location-mapping.json"));

	@Test
	void 대상_대상밖_미확인을_구분한다() {
		ApartmentTradeScopeClassifier classifier =
			new ApartmentTradeScopeClassifier(reference, mapping);

		assertThat(classifier.classify("11170", "한남동"))
			.isEqualTo(new ApartmentTradeScope(
				Status.IN_SCOPE, "1117013100", "yongsan-hannam"
			));
		assertThat(classifier.classify("41370", "오산동"))
			.isEqualTo(new ApartmentTradeScope(
				Status.OUT_OF_SCOPE, "4137010100", null
			));
		assertThat(classifier.classify("11170", "없는동"))
			.isEqualTo(new ApartmentTradeScope(
				Status.UNKNOWN_LEGAL_DONG, null, null
			));
		assertThat(classifier.classify("41220", "고덕면 궁리").status())
			.isEqualTo(Status.OUT_OF_SCOPE);
	}

	@Test
	void 참조_자료에_없는_권역_코드가_있으면_실패한다() {
		LocationMapping invalidMapping = new LocationMapping(
			List.of(new RegionDefinition("seoul", "서울", true, 1)),
			List.of(new CityDefinition("11170", "seoul", "용산구")),
			List.of(new ZoneDefinition(
				"unknown-zone", "11170", "확인용",
				List.of("1117019999"), new BigDecimal("3.0")
			))
		);

		assertThatThrownBy(() -> new ApartmentTradeScopeClassifier(
			reference, invalidMapping
		))
			.isInstanceOf(LocationMappingException.class)
			.hasMessageContaining("참조 자료에 없는 권역 코드");
	}
}
