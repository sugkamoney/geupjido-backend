package com.geupjido.batch.trade.service;

import java.io.IOException;
import java.io.Writer;
import java.nio.file.Path;
import java.util.EnumMap;
import java.util.List;
import java.util.Map;

import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Sort;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import com.geupjido.batch.location.reader.LegalDongCodeReferenceReader;
import com.geupjido.batch.location.reader.LocationMappingReader;
import com.geupjido.batch.trade.model.ApartmentTradeAuditResult;
import com.geupjido.batch.trade.model.ApartmentTradeCandidateAssessment;
import com.geupjido.batch.trade.model.ApartmentTradeCandidateAssessment.Status;
import com.geupjido.batch.trade.model.ApartmentTradeScope;
import com.geupjido.trade.domain.ApartmentTrade;
import com.geupjido.trade.repository.ApartmentTradeRepository;

/** 저장된 모든 거래를 페이지 단위로 읽어 연결 상태를 TSV로 기록한다. */
@Service
public class ApartmentTradeAuditService {

	private static final int PAGE_SIZE = 200;
	private static final String HEADER =
		"거래ID\t계약일\t시군구코드\t법정동명\t거래단지명\t지번\t서비스범위\t법정동코드\t권역ID\t연결상태\t후보판정\t후보단지ID\t연결단지ID\t연결권역ID\n";

	private final LegalDongCodeReferenceReader referenceReader;
	private final LocationMappingReader mappingReader;
	private final ApartmentTradeRepository tradeRepository;
	private final ApartmentTradeCandidateAssessmentService assessmentService;

	public ApartmentTradeAuditService(
		LegalDongCodeReferenceReader referenceReader,
		LocationMappingReader mappingReader,
		ApartmentTradeRepository tradeRepository,
		ApartmentTradeCandidateAssessmentService assessmentService
	) {
		this.referenceReader = referenceReader;
		this.mappingReader = mappingReader;
		this.tradeRepository = tradeRepository;
		this.assessmentService = assessmentService;
	}

	@Transactional(readOnly = true)
	public ApartmentTradeAuditResult auditAll(
		Path referencePath,
		Path mappingPath,
		Writer writer
	) throws IOException {
		if (writer == null) {
			throw new IllegalArgumentException("점검 결과를 기록할 Writer가 필요합니다.");
		}
		ApartmentTradeScopeClassifier classifier =
			new ApartmentTradeScopeClassifier(
				referenceReader.read(referencePath),
				mappingReader.read(mappingPath)
			);
		writer.write(HEADER);

		long total = 0;
		long inScope = 0;
		long outOfScope = 0;
		long unknown = 0;
		long linked = 0;
		long inconsistent = 0;
		Map<Status, Long> candidateCounts = new EnumMap<>(Status.class);
		int pageNumber = 0;
		Page<ApartmentTrade> page;
		do {
			page = tradeRepository.findAll(PageRequest.of(
				pageNumber++, PAGE_SIZE, Sort.by("id")
			));
			for (ApartmentTrade trade : page.getContent()) {
				total++;
				ApartmentTradeScope scope = classifier.classify(
					trade.getDistrictCode(), trade.getLegalDongName()
				);
				switch (scope.status()) {
					case IN_SCOPE -> inScope++;
					case OUT_OF_SCOPE -> outOfScope++;
					case UNKNOWN_LEGAL_DONG -> unknown++;
				}

				String linkStatus = "UNLINKED";
				String assessmentStatus = "";
				List<String> candidateIds = List.of();
				if (trade.getComplex() != null || trade.getZone() != null) {
					linked++;
					boolean consistent = trade.getComplex() != null
						&& trade.getZone() != null
						&& trade.getComplex().getZone() != null
						&& scope.status() == ApartmentTradeScope.Status.IN_SCOPE
						&& trade.getZone().getId().equals(
							trade.getComplex().getZone().getId()
						)
						&& trade.getZone().getId().equals(scope.zoneId());
					linkStatus = consistent ? "LINKED" : "INCONSISTENT_LINK";
					if (!consistent) {
						inconsistent++;
					}
				} else if (scope.status() == ApartmentTradeScope.Status.IN_SCOPE) {
					ApartmentTradeCandidateAssessment assessment =
						assessmentService.assess(trade, scope);
					assessmentStatus = assessment.status().name();
					candidateIds = assessment.candidateIds();
					candidateCounts.merge(assessment.status(), 1L, Long::sum);
				}
				writeRow(writer, trade, scope, linkStatus,
					assessmentStatus, candidateIds);
			}
		} while (page.hasNext());

		return new ApartmentTradeAuditResult(
			total, inScope, outOfScope, unknown, linked,
			inconsistent, Map.copyOf(candidateCounts)
		);
	}

	private static void writeRow(
		Writer writer,
		ApartmentTrade trade,
		ApartmentTradeScope scope,
		String linkStatus,
		String assessmentStatus,
		List<String> candidateIds
	) throws IOException {
		String linkedComplexId = trade.getComplex() == null
			? "" : trade.getComplex().getId();
		String linkedZoneId = trade.getZone() == null
			? "" : trade.getZone().getId();
		String[] fields = {
			String.valueOf(trade.getId()),
			String.valueOf(trade.getDealDate()),
			trade.getDistrictCode(), trade.getLegalDongName(),
			trade.getRawApartmentName(), trade.getLotNumber(),
			scope.status().name(), scope.legalDongCode(),
			scope.zoneId(), linkStatus, assessmentStatus,
			String.join(",", candidateIds),
			linkedComplexId, linkedZoneId
		};
		for (int index = 0; index < fields.length; index++) {
			if (index > 0) {
				writer.write('\t');
			}
			writer.write(tsvValue(fields[index]));
		}
		writer.write('\n');
	}

	private static String tsvValue(String value) {
		return value == null ? ""
			: value.replace('\t', ' ').replace('\n', ' ')
				.replace('\r', ' ');
	}
}
