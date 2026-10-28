package com.geupjido.batch.trade.service;

import java.nio.file.Path;
import java.time.YearMonth;
import java.util.List;

import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import com.geupjido.batch.location.reader.LegalDongCodeReferenceReader;
import com.geupjido.batch.location.reader.LocationMappingReader;
import com.geupjido.batch.trade.model.ApartmentTradeCandidateAssessment;
import com.geupjido.batch.trade.model.ApartmentTradeLinkResult;
import com.geupjido.batch.trade.model.ApartmentTradeMatchProposal;
import com.geupjido.batch.trade.model.ApartmentTradeScope;
import com.geupjido.batch.trade.reader.ApartmentTradeMatchProposalReader;
import com.geupjido.complex.domain.Complex;
import com.geupjido.complex.repository.ComplexRepository;
import com.geupjido.trade.domain.ApartmentTrade;
import com.geupjido.trade.repository.ApartmentTradeRepository;

/**
 * 승인된 원본 거래·단지 대응만 기존 거래에 적용한다.
 */
@Service
public class ApartmentTradeLinkService {

	private final ApartmentTradeMatchProposalReader proposalReader;
	private final LegalDongCodeReferenceReader referenceReader;
	private final LocationMappingReader mappingReader;
	private final ApartmentTradeCandidateAssessmentService assessmentService;
	private final ApartmentTradeRepository tradeRepository;
	private final ComplexRepository complexRepository;

	public ApartmentTradeLinkService(
		ApartmentTradeMatchProposalReader proposalReader,
		LegalDongCodeReferenceReader referenceReader,
		LocationMappingReader mappingReader,
		ApartmentTradeCandidateAssessmentService assessmentService,
		ApartmentTradeRepository tradeRepository,
		ComplexRepository complexRepository
	) {
		this.proposalReader = proposalReader;
		this.referenceReader = referenceReader;
		this.mappingReader = mappingReader;
		this.assessmentService = assessmentService;
		this.tradeRepository = tradeRepository;
		this.complexRepository = complexRepository;
	}

	@Transactional
	public ApartmentTradeLinkResult linkApproved(
		Path proposalPath,
		Path referencePath,
		Path mappingPath
	) {
		var proposals = proposalReader.read(proposalPath);
		var classifier = new ApartmentTradeScopeClassifier(
			referenceReader.read(referencePath),
			mappingReader.read(mappingPath)
		);

		int pending = 0;
		int rejected = 0;
		int approved = 0;
		int linked = 0;
		int alreadyLinked = 0;

		for (ApartmentTradeMatchProposal proposal : proposals.values()) {
			switch (proposal.decision()) {
				case PENDING -> pending++;
				case REJECTED -> rejected++;
				case APPROVED -> {
					approved++;
					Complex candidate = validateCandidate(proposal);
					List<ApartmentTrade> trades = findTrades(proposal);
					if (trades.isEmpty()) {
						throw new IllegalStateException(
							"승인된 연결 제안의 원본 거래가 없습니다: "
								+ proposal.key()
						);
					}
					for (ApartmentTrade trade : trades) {
						ApartmentTradeScope scope = classifier.classify(
							trade.getDistrictCode(), trade.getLegalDongName()
						);
						validateAssessment(proposal, trade, scope);
						if (trade.linkToComplex(candidate)) {
							linked++;
						} else {
							alreadyLinked++;
						}
					}
				}
			}
		}
		return new ApartmentTradeLinkResult(
			proposals.size(), pending, rejected, approved,
			linked, alreadyLinked
		);
	}

	private Complex validateCandidate(ApartmentTradeMatchProposal proposal) {
		Complex candidate = complexRepository.findById(
			proposal.candidateId()
		).orElseThrow(() -> new IllegalStateException(
			"승인된 후보 단지가 없습니다: " + proposal.candidateId()
		));
		if (!proposal.candidateName().equals(candidate.getName())
			|| !proposal.key().legalDongCode().equals(
				candidate.getLegalDongCode()
			)
			|| candidate.getApprovalDate() == null
			|| candidate.getApprovalDate().getYear()
				!= proposal.key().buildYear()
			|| candidate.getZone() == null) {
			throw new IllegalStateException(
				"승인된 후보 단지의 이름·법정동·연도·권역이 변경되었습니다: "
					+ proposal.candidateId()
			);
		}
		return candidate;
	}

	private List<ApartmentTrade> findTrades(
		ApartmentTradeMatchProposal proposal
	) {
		var key = proposal.key();
		YearMonth dealYearMonth = key.dealYearMonth();
		return tradeRepository
			.findByDistrictCodeAndLegalDongNameAndRawApartmentNameAndLotNumberAndBuildYearAndDealDateGreaterThanEqualAndDealDateLessThan(
				key.legalDongCode().substring(0, 5),
				key.legalDongName(), key.rawApartmentName(),
				key.lotNumber(), key.buildYear(),
				dealYearMonth.atDay(1),
				dealYearMonth.plusMonths(1).atDay(1)
			);
	}

	private void validateAssessment(
		ApartmentTradeMatchProposal proposal,
		ApartmentTrade trade,
		ApartmentTradeScope scope
	) {
		if (scope.status() != ApartmentTradeScope.Status.IN_SCOPE
			|| !proposal.key().legalDongCode().equals(
				scope.legalDongCode()
			)) {
			throw new IllegalStateException(
				"승인된 원본 거래가 서비스 대상 법정동과 맞지 않습니다: "
					+ trade.getId()
			);
		}
		ApartmentTradeCandidateAssessment assessment =
			assessmentService.assess(trade, scope);
		if (assessment.status() != proposal.assessmentStatus()
			|| !assessment.candidateIds().equals(
				List.of(proposal.candidateId())
			)) {
			throw new IllegalStateException(
				"승인된 원본 거래의 후보 판정이 변경되었습니다: "
					+ trade.getId()
			);
		}
	}
}
