package com.geupjido.batch.complex.runner;

import org.locationtech.jts.geom.Point;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.boot.ApplicationArguments;
import org.springframework.boot.ApplicationRunner;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.core.env.Environment;
import org.springframework.stereotype.Component;
import org.springframework.util.Assert;

import com.geupjido.batch.complex.dto.NaverGeocodingResponse;
import com.geupjido.batch.complex.mapper.NaverGeocodingPointMapper;
import com.geupjido.batch.complex.service.ComplexGeocodingLookupService;
import com.geupjido.batch.complex.service.ComplexZoneMatchService;
import com.geupjido.complex.domain.Complex;
import com.geupjido.complex.repository.ComplexRepository;
import com.geupjido.zone.domain.Zone;

/**
 * 단지 한 건의 지오코딩 결과를 DB 변경 없이 확인하는 로컬 수동 실행 도구다.
 */
@Component
@ConditionalOnProperty(
	prefix = "app.geocoding-probe",
	name = "enabled",
	havingValue = "true"
)
public class GeocodingProbeRunner implements ApplicationRunner {

	private static final Logger log =
		LoggerFactory.getLogger(GeocodingProbeRunner.class);

	private final Environment environment;
	private final ComplexRepository complexRepository;
	private final ComplexGeocodingLookupService lookupService;
	private final NaverGeocodingPointMapper pointMapper;
	private final ComplexZoneMatchService zoneMatchService;

	public GeocodingProbeRunner(
		Environment environment,
		ComplexRepository complexRepository,
		ComplexGeocodingLookupService lookupService,
		NaverGeocodingPointMapper pointMapper,
		ComplexZoneMatchService zoneMatchService
	) {
		this.environment = environment;
		this.complexRepository = complexRepository;
		this.lookupService = lookupService;
		this.pointMapper = pointMapper;
		this.zoneMatchService = zoneMatchService;
	}

	@Override
	public void run(ApplicationArguments args) {
		String complexId = environment.getProperty(
			"app.geocoding-probe.complex-id"
		);
		Assert.hasText(complexId, "검증할 단지 코드가 필요합니다.");

		Complex complex = complexRepository.findById(complexId)
			.orElseThrow(() -> new IllegalArgumentException(
				"검증할 단지를 찾을 수 없습니다: " + complexId
			));

		NaverGeocodingResponse.Address address = lookupService
			.findAddress(complex)
			.orElseThrow(() -> new IllegalStateException(
				"도로명·지번주소 모두 지오코딩 결과가 없습니다: "
					+ complexId
			));
		Point point = pointMapper.map(address);
		ComplexZoneMatchService.MatchResult matchResult =
			zoneMatchService.match(complex, point);

		log.info(
			"단지 지오코딩 검증 결과: 단지코드={}, 법정동코드={}, "
				+ "경도={}, 위도={}, SRID={}, 응답 도로명주소={}, "
				+ "응답 지번주소={}",
			complex.getId(),
			complex.getLegalDongCode(),
			point.getX(),
			point.getY(),
			point.getSRID(),
			address.roadAddress(),
			address.jibunAddress()
		);
		log.info(
			"단지 권역 판정 결과: 단지코드={}, 상태={}, 후보 권역={}",
			complex.getId(),
			matchResult.status(),
			matchResult.candidates().stream().map(Zone::getId).toList()
		);
	}
}
