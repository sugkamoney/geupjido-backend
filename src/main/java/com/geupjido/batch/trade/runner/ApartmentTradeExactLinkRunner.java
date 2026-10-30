package com.geupjido.batch.trade.runner;

import java.nio.file.Files;
import java.nio.file.Path;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.boot.ApplicationArguments;
import org.springframework.boot.ApplicationRunner;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.stereotype.Component;

import com.geupjido.batch.trade.config.ApartmentTradeMatchProperties;
import com.geupjido.batch.trade.service.ApartmentTradeExactLinkService;
import com.geupjido.batch.trade.service.ComplexRepresentativePriceService;

/** 명시적으로 켤 때만 검증 가능한 거래를 연결하고 대표 가격을 재계산한다. */
@Component
@ConditionalOnProperty(
	prefix = "app.apartment-trade-match",
	name = "auto-exact-enabled",
	havingValue = "true"
)
public class ApartmentTradeExactLinkRunner implements ApplicationRunner {

	private static final Logger log =
		LoggerFactory.getLogger(ApartmentTradeExactLinkRunner.class);

	private final ApartmentTradeMatchProperties properties;
	private final ApartmentTradeExactLinkService linkService;
	private final ComplexRepresentativePriceService priceService;

	public ApartmentTradeExactLinkRunner(
		ApartmentTradeMatchProperties properties,
		ApartmentTradeExactLinkService linkService,
		ComplexRepresentativePriceService priceService
	) {
		this.properties = properties;
		this.linkService = linkService;
		this.priceService = priceService;
	}

	@Override
	public void run(ApplicationArguments args) {
		Path referencePath = requireFile(
			properties.referencePath(), "법정동 코드 참조 파일"
		);
		Path mappingPath = requireFile(
			properties.mappingPath(), "권역 매핑 파일"
		);
		log.info("실거래가 엄격 일치 단지 연결 시작");
		var link = linkService.linkExact(referencePath, mappingPath);
		log.info(
			"실거래가 엄격 일치 단지 연결 완료. 임대 전용 연결 해제={}, "
				+ "미연결 검사={}, 대상={}, "
				+ "대상 밖={}, 법정동 미확인={}, 신규 연결={}, "
				+ "권역 불일치={}, 후보 판정={}",
			link.detachedRentalLinks(), link.scanned(),
			link.inScope(), link.outOfScope(),
			link.unknown(), link.linked(), link.zoneMismatch(),
			link.assessments()
		);
		var price = priceService.recalculateAll();
		log.info(
			"엄격 일치 연결 후 대표 시세 재계산 완료. 전체 단지={}, "
				+ "가격 있음={}, 순위 있음={}, 적격 거래 없음={}",
			price.totalComplexCount(), price.pricedComplexCount(),
			price.rankedComplexCount(), price.withoutEligibleTradeCount()
		);
	}

	private static Path requireFile(Path path, String name) {
		if (path == null || !Files.isRegularFile(path)) {
			throw new IllegalStateException(name + "이(가) 없습니다: " + path);
		}
		return path;
	}
}
