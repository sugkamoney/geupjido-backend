package com.geupjido.batch.trade;

import java.io.StringWriter;
import java.math.BigDecimal;
import java.nio.file.Files;
import java.nio.file.Path;
import java.time.LocalDate;
import java.time.YearMonth;
import java.util.List;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.condition.EnabledIfEnvironmentVariable;
import org.junit.jupiter.api.io.TempDir;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.transaction.annotation.Transactional;

import com.geupjido.batch.location.reader.LegalDongCodeReferenceReader;
import com.geupjido.batch.location.reader.LocationMappingReader;
import com.geupjido.batch.trade.model.ApartmentTradeMatchProposal.Key;
import com.geupjido.batch.trade.reader.ApartmentTradeMatchProposalReader;
import com.geupjido.batch.trade.service.ApartmentTradeCandidateAssessmentService;
import com.geupjido.batch.trade.service.ApartmentTradeAuditService;
import com.geupjido.batch.trade.service.ApartmentTradeLinkService;
import com.geupjido.batch.trade.service.ApartmentTradeScopeClassifier;
import com.geupjido.complex.repository.ComplexRepository;
import com.geupjido.trade.domain.ApartmentTrade;
import com.geupjido.trade.repository.ApartmentTradeRepository;

import static org.assertj.core.api.Assertions.assertThat;

@EnabledIfEnvironmentVariable(named = "RUN_POSTGIS_IT", matches = "true")
@SpringBootTest
@ActiveProfiles("local")
@Transactional(readOnly = true)
class ApartmentTradeMatchProposalIT {

	@Autowired
	private ApartmentTradeRepository tradeRepository;
	@Autowired
	private ComplexRepository complexRepository;
	@Autowired
	private ApartmentTradeCandidateAssessmentService assessmentService;
	@Autowired
	private ApartmentTradeMatchProposalReader proposalReader;
	@Autowired
	private LegalDongCodeReferenceReader referenceReader;
	@Autowired
	private LocationMappingReader mappingReader;
	@Autowired
	private ApartmentTradeLinkService linkService;
	@Autowired
	private ApartmentTradeAuditService auditService;

	@Test
	void 용산_제안_14개가_실제_거래와_단지_후보에_맞는다() {
		var proposals = proposalReader.read(Path.of(
			"data/trade/matching/yongsan-202609-proposals.tsv"
		));
		var classifier = new ApartmentTradeScopeClassifier(
			referenceReader.read(Path.of(
				"data/location/reference/legal-dongs-20260917.tsv"
			)),
			mappingReader.read(Path.of(
				"data/location/mapping/location-mapping.json"
			))
		);

		List<ApartmentTrade> trades = tradeRepository.findAll();
		int verifiedTradeCount = 0;
		for (var proposal : proposals.values()) {
			var complex = complexRepository.findById(
				proposal.candidateId()
			).orElseThrow();
			assertThat(complex.getName())
				.isEqualTo(proposal.candidateName());
			assertThat(complex.getLegalDongCode())
				.isEqualTo(proposal.key().legalDongCode());
			assertThat(complex.getApprovalDate().getYear())
				.isEqualTo(proposal.key().buildYear());
			assertThat(complex.getZone()).isNotNull();

			List<ApartmentTrade> matchingTrades = trades.stream()
				.filter(trade -> matches(trade, proposal.key()))
				.toList();
			assertThat(matchingTrades).isNotEmpty();
			for (ApartmentTrade trade : matchingTrades) {
				var scope = classifier.classify(
					trade.getDistrictCode(), trade.getLegalDongName()
				);
				assertThat(scope.legalDongCode())
					.isEqualTo(proposal.key().legalDongCode());
				var assessment = assessmentService.assess(trade, scope);
				assertThat(assessment.status())
					.isEqualTo(proposal.assessmentStatus());
				assertThat(assessment.candidateIds())
					.containsExactly(proposal.candidateId());
				verifiedTradeCount++;
			}
		}
		assertThat(verifiedTradeCount).isEqualTo(18);
	}

	@Test
	@Transactional
	void 승인된_12조합만_연결한다() {
		var result = linkService.linkApproved(
			Path.of("data/trade/matching/yongsan-202609-proposals.tsv"),
			Path.of("data/location/reference/legal-dongs-20260917.tsv"),
			Path.of("data/location/mapping/location-mapping.json")
		);
		assertThat(result.proposalCount()).isEqualTo(14);
		assertThat(result.pendingCount()).isEqualTo(2);
		assertThat(result.approvedCount()).isEqualTo(12);
		assertThat(result.linkedTradeCount()
			+ result.alreadyLinkedTradeCount()).isEqualTo(16);
	}

	@Test
	@Transactional
	void 계약월별_승인_범위를_지키고_재실행해도_중복되지_않는다(
		@TempDir Path dir
	) throws Exception {
		Path proposalPath = Path.of(
			"data/trade/matching/yongsan-202609-proposals.tsv"
		);
		ApartmentTrade octoberTrade = tradeRepository.saveAndFlush(
			new ApartmentTrade(
				LocalDate.of(2026, 10, 1), 999999,
				BigDecimal.valueOf(84), (short) 7, (short) 2004,
				"11170", "한남동", "한남동리첸시아", "72-1",
				null, null, false, null
			)
		);

		Path referencePath = Path.of(
			"data/location/reference/legal-dongs-20260917.tsv"
		);
		Path mappingPath = Path.of(
			"data/location/mapping/location-mapping.json"
		);
		var first = linkService.linkApproved(
			proposalPath, referencePath, mappingPath
		);
		assertThat(first.approvedCount()).isEqualTo(12);
		assertThat(first.pendingCount()).isEqualTo(2);
		assertThat(first.linkedTradeCount()
			+ first.alreadyLinkedTradeCount()).isEqualTo(16);

		var second = linkService.linkApproved(
			proposalPath, referencePath, mappingPath
		);
		assertThat(second.linkedTradeCount()).isZero();
		assertThat(second.alreadyLinkedTradeCount()).isEqualTo(16);

		var linked = tradeRepository
			.findByDistrictCodeAndLegalDongNameAndRawApartmentNameAndLotNumberAndBuildYear(
				"11170", "한남동", "한남동리첸시아", "72-1", (short) 2004
			);
		assertThat(linked).hasSize(3);
		assertThat(octoberTrade.getComplex()).isNull();
		assertThat(octoberTrade.getZone()).isNull();
		List<ApartmentTrade> septemberTrades = linked.stream().filter(trade ->
			trade.getDealDate().getMonthValue() == 9
		).toList();
		assertThat(septemberTrades).hasSize(2).allSatisfy(trade -> {
			assertThat(trade.getComplex().getId()).isEqualTo("A14021001");
			assertThat(trade.getZone().getId())
				.isEqualTo(trade.getComplex().getZone().getId());
		});

		Path bothMonths = dir.resolve("both-months.tsv");
		String original = Files.readString(proposalPath);
		String octoberRow = original.lines().skip(1).findFirst()
			.orElseThrow().replaceFirst("^202609", "202610");
		Files.writeString(bothMonths, original + octoberRow + "\n");
		var bothMonthsResult = linkService.linkApproved(
			bothMonths, referencePath, mappingPath
		);
		assertThat(bothMonthsResult.approvedCount()).isEqualTo(13);
		assertThat(bothMonthsResult.linkedTradeCount()).isEqualTo(1);
		assertThat(bothMonthsResult.alreadyLinkedTradeCount())
			.isEqualTo(16);
		assertThat(octoberTrade.getComplex().getId())
			.isEqualTo("A14021001");
		assertThat(octoberTrade.getZone().getId())
			.isEqualTo(octoberTrade.getComplex().getZone().getId());
	}

	@Test
	void 저장된_거래_전체를_읽기_전용으로_점검한다() throws Exception {
		StringWriter report = new StringWriter();
		var result = auditService.auditAll(
			Path.of("data/location/reference/legal-dongs-20260917.tsv"),
			Path.of("data/location/mapping/location-mapping.json"),
			report
		);
		assertThat(result.totalCount()).isEqualTo(tradeRepository.count());
		assertThat(result.totalCount()).isEqualTo(
			result.inScopeCount() + result.outOfScopeCount()
				+ result.unknownLegalDongCount()
		);
		assertThat(result.inconsistentLinkCount()).isZero();
		assertThat(report.toString().lines().count())
			.isEqualTo(result.totalCount() + 1);
		assertThat(report.toString())
			.contains("거래ID\t계약일\t시군구코드")
			.contains("OUT_OF_SCOPE")
			.contains("NO_CANDIDATE");
	}

	private static boolean matches(ApartmentTrade trade, Key key) {
		return trade.getBuildYear() != null
			&& YearMonth.from(trade.getDealDate()).equals(key.dealYearMonth())
			&& trade.getDistrictCode().equals(key.legalDongCode().substring(0, 5))
			&& trade.getLegalDongName().equals(key.legalDongName())
			&& key.lotNumber().equals(trade.getLotNumber())
			&& trade.getRawApartmentName().equals(key.rawApartmentName())
			&& trade.getBuildYear() == key.buildYear();
	}
}
