package com.geupjido.batch.trade.runner;

import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.StandardCopyOption;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.boot.ApplicationArguments;
import org.springframework.boot.ApplicationRunner;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.stereotype.Component;

import com.geupjido.batch.trade.config.ApartmentTradeMatchProperties;
import com.geupjido.batch.trade.model.ApartmentTradeAuditResult;
import com.geupjido.batch.trade.service.ApartmentTradeAuditService;

/** 기본 비활성 수동 점검이다. 거래나 단지의 DB 값을 변경하지 않는다. */
@Component
@ConditionalOnProperty(
	prefix = "app.apartment-trade-match",
	name = "audit-enabled",
	havingValue = "true"
)
public class ApartmentTradeAuditRunner implements ApplicationRunner {

	private static final Logger log =
		LoggerFactory.getLogger(ApartmentTradeAuditRunner.class);

	private final ApartmentTradeMatchProperties properties;
	private final ApartmentTradeAuditService auditService;

	public ApartmentTradeAuditRunner(
		ApartmentTradeMatchProperties properties,
		ApartmentTradeAuditService auditService
	) {
		this.properties = properties;
		this.auditService = auditService;
	}

	@Override
	public void run(ApplicationArguments args) throws Exception {
		Path referencePath = requireFile(
			properties.referencePath(), "법정동 코드 참조 파일"
		);
		Path mappingPath = requireFile(
			properties.mappingPath(), "권역 매핑 파일"
		);
		Path outputPath = properties.auditPath();
		if (outputPath == null) {
			throw new IllegalStateException("실거래가 점검 결과 파일 경로가 없습니다.");
		}
		Path parent = outputPath.toAbsolutePath().getParent();
		Files.createDirectories(parent);
		Path temporaryPath = Files.createTempFile(
			parent, "apartment-trade-audit-", ".tmp"
		);
		ApartmentTradeAuditResult result;
		try {
			try (var writer = Files.newBufferedWriter(temporaryPath)) {
				result = auditService.auditAll(
					referencePath, mappingPath, writer
				);
			}
			Files.move(temporaryPath, outputPath,
				StandardCopyOption.REPLACE_EXISTING);
		} finally {
			Files.deleteIfExists(temporaryPath);
		}
		log.info(
			"실거래가 전체 점검 완료. 전체={}, 대상={}, 대상 밖={}, "
				+ "법정동 미확인={}, 연결={}, 연결 불일치={}, "
				+ "미연결 후보 판정={}, 결과 파일={}",
			result.totalCount(), result.inScopeCount(),
			result.outOfScopeCount(), result.unknownLegalDongCount(),
			result.linkedCount(), result.inconsistentLinkCount(),
			result.unlinkedCandidateCounts(), outputPath
		);
	}

	private static Path requireFile(Path path, String name) {
		if (path == null || !Files.isRegularFile(path)) {
			throw new IllegalStateException(name + "이(가) 없습니다: " + path);
		}
		return path;
	}
}
