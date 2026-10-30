package com.geupjido.batch.trade.service;

import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.stereotype.Component;

import com.geupjido.batch.location.reader.LegalDongCodeReferenceReader;
import com.geupjido.batch.location.reader.LocationMappingReader;
import com.geupjido.batch.trade.config.ApartmentTradeMatchProperties;
import com.geupjido.batch.trade.model.ApartmentTradeScope;

/** 시군구 API 응답에서 서비스 법정동의 거래만 저장하도록 판정한다. */
@Component
@ConditionalOnProperty(prefix = "app.external.apartment-trade",
	name = "enabled", havingValue = "true")
public class ApartmentTradeScopeFilter {

	private final ApartmentTradeScopeClassifier classifier;

	public ApartmentTradeScopeFilter(
		LegalDongCodeReferenceReader referenceReader,
		LocationMappingReader mappingReader,
		ApartmentTradeMatchProperties properties
	) {
		classifier = new ApartmentTradeScopeClassifier(
			referenceReader.read(properties.referencePath()),
			mappingReader.read(properties.mappingPath())
		);
	}

	public boolean includes(String districtCode, String legalDongName) {
		String normalizedDistrict = districtCode == null ? null : districtCode.trim();
		String normalizedDong = legalDongName == null ? null : legalDongName.trim();
		return classifier.classify(normalizedDistrict, normalizedDong).status()
			== ApartmentTradeScope.Status.IN_SCOPE;
	}
}
