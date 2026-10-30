package com.geupjido.batch.trade.runner;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.boot.ApplicationArguments;
import org.springframework.boot.ApplicationRunner;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.stereotype.Component;

import com.geupjido.batch.trade.model.ComplexRepresentativePriceResult;
import com.geupjido.batch.trade.service.ComplexRepresentativePriceService;

/** 명시적으로 활성화할 때만 대표 시세와 잠정 순위를 수동 재계산한다. */
@Component
@ConditionalOnProperty(
	prefix = "app.complex-price-recalculation",
	name = "enabled",
	havingValue = "true"
)
public class ComplexRepresentativePriceRunner implements ApplicationRunner {

	private static final Logger log =
		LoggerFactory.getLogger(ComplexRepresentativePriceRunner.class);

	private final ComplexRepresentativePriceService service;

	public ComplexRepresentativePriceRunner(
		ComplexRepresentativePriceService service
	) {
		this.service = service;
	}

	@Override
	public void run(ApplicationArguments args) {
		log.info("단지 대표 시세·㎡당 가격 기준 잠정 순위 재계산 시작");
		ComplexRepresentativePriceResult result = service.recalculateAll();
		log.info(
			"단지 대표 시세·잠정 순위 재계산 완료. 전체={}, 대표 거래 있음={}, "
				+ "순위 있음={}, 적격 거래 없음={}",
			result.totalComplexCount(), result.pricedComplexCount(),
			result.rankedComplexCount(), result.withoutEligibleTradeCount()
		);
	}
}
