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
import com.geupjido.batch.trade.model.ApartmentTradeLinkResult;
import com.geupjido.batch.trade.service.ApartmentTradeLinkService;

/** 수동으로 승인된 거래·단지 연결을 적용한다. 일반 서버 실행에는 참여하지 않는다. */
@Component
@ConditionalOnProperty(
	prefix = "app.apartment-trade-match",
	name = "enabled",
	havingValue = "true"
)
public class ApartmentTradeMatchRunner implements ApplicationRunner {

	private static final Logger log =
		LoggerFactory.getLogger(ApartmentTradeMatchRunner.class);

	private final ApartmentTradeMatchProperties properties;
	private final ApartmentTradeLinkService linkService;

	public ApartmentTradeMatchRunner(
		ApartmentTradeMatchProperties properties,
		ApartmentTradeLinkService linkService
	) {
		this.properties = properties;
		this.linkService = linkService;
	}

	@Override
	public void run(ApplicationArguments args) {
		Path proposalPath = requireFile(
			properties.proposalPath(), "거래·단지 검토 파일"
		);
		Path referencePath = requireFile(
			properties.referencePath(), "법정동 코드 참조 파일"
		);
		Path mappingPath = requireFile(
			properties.mappingPath(), "권역 매핑 파일"
		);
		ApartmentTradeLinkResult result = linkService.linkApproved(
			proposalPath, referencePath, mappingPath
		);
		log.info(
			"실거래가 단지 연결 완료. 제안={}, 검토중={}, 거절={}, 승인={}, "
				+ "신규 연결={}, 이미 연결={}",
			result.proposalCount(), result.pendingCount(),
			result.rejectedCount(), result.approvedCount(),
			result.linkedTradeCount(), result.alreadyLinkedTradeCount()
		);
	}

	private static Path requireFile(Path path, String name) {
		if (path == null || !Files.isRegularFile(path)) {
			throw new IllegalStateException(name + "이(가) 없습니다: " + path);
		}
		return path;
	}
}
