package com.geupjido.batch.complex.runner;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.ApplicationArguments;
import org.springframework.boot.ApplicationRunner;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.stereotype.Component;

import com.geupjido.batch.complex.service.ComplexSaleTypeBackfillService;

/** 명시적으로 활성화했을 때만 기존 단지의 분양형태를 채운다. */
@Component
@ConditionalOnProperty(prefix = "app.complex-sale-type-backfill",
	name = "enabled", havingValue = "true")
public class ComplexSaleTypeBackfillRunner implements ApplicationRunner {

	private static final Logger log = LoggerFactory.getLogger(
		ComplexSaleTypeBackfillRunner.class);

	private final ComplexSaleTypeBackfillService service;
	private final String afterId;
	private final int maxCount;
	private final boolean onlyUnpriced;

	public ComplexSaleTypeBackfillRunner(ComplexSaleTypeBackfillService service,
		@Value("${app.complex-sale-type-backfill.after-id:}") String afterId,
		@Value("${app.complex-sale-type-backfill.max-count:100}") int maxCount,
		@Value("${app.complex-sale-type-backfill.only-unpriced:false}")
			boolean onlyUnpriced
	) {
		this.service = service;
		this.afterId = afterId;
		this.maxCount = maxCount;
		this.onlyUnpriced = onlyUnpriced;
	}

	@Override
	public void run(ApplicationArguments args) {
		var result = service.backfill(afterId, maxCount, onlyUnpriced);
		log.info("단지 분양형태 적재 결과: 선택={}, 갱신={}, 제외 분류 삭제={}, "
			+ "실패 ID={}, 마지막 ID={}, 한도 초과={}, API 04 다수={}",
			result.selected(), result.updated(), result.excluded(),
			result.failedIds(), result.lastId(), result.quotaExceeded(),
			result.apiUnavailable());
	}
}
