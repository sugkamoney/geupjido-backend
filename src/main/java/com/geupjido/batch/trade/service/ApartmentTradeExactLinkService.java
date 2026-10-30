package com.geupjido.batch.trade.service;

import java.nio.file.Path;
import java.util.ArrayList;
import java.util.Collection;
import java.util.EnumMap;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;

import org.springframework.data.domain.PageRequest;
import org.springframework.stereotype.Service;
import org.springframework.transaction.support.TransactionTemplate;

import com.geupjido.batch.location.reader.LegalDongCodeReferenceReader;
import com.geupjido.batch.location.reader.LocationMappingReader;
import com.geupjido.batch.trade.model.ApartmentTradeCandidateAssessment.Status;
import com.geupjido.batch.trade.model.ApartmentTradeScope;
import com.geupjido.complex.domain.Complex;
import com.geupjido.complex.repository.ComplexRepository;
import com.geupjido.trade.domain.ApartmentTrade;
import com.geupjido.trade.repository.ApartmentTradeRepository;

/** 엄격 일치 또는 기존 검증 거래와 원본 식별 조합이 같은 거래만 연결한다. */
@Service
public class ApartmentTradeExactLinkService {

	private static final int BATCH_SIZE = 500;

	private final LegalDongCodeReferenceReader referenceReader;
	private final LocationMappingReader mappingReader;
	private final ApartmentTradeRepository tradeRepository;
	private final ComplexRepository complexRepository;
	private final ApartmentTradeCandidateFinder candidateFinder;
	private final ApartmentTradeCandidateAssessmentService assessmentService;
	private final TransactionTemplate transactionTemplate;

	public ApartmentTradeExactLinkService(
		LegalDongCodeReferenceReader referenceReader,
		LocationMappingReader mappingReader,
		ApartmentTradeRepository tradeRepository,
		ComplexRepository complexRepository,
		ApartmentTradeCandidateFinder candidateFinder,
		ApartmentTradeCandidateAssessmentService assessmentService,
		TransactionTemplate transactionTemplate
	) {
		this.referenceReader = referenceReader;
		this.mappingReader = mappingReader;
		this.tradeRepository = tradeRepository;
		this.complexRepository = complexRepository;
		this.candidateFinder = candidateFinder;
		this.assessmentService = assessmentService;
		this.transactionTemplate = transactionTemplate;
	}

	public Result linkExact(Path referencePath, Path mappingPath) {
		Integer detached = transactionTemplate.execute(
			ignored -> tradeRepository.detachRentalOnlyLinks()
		);
		Map<SourceKey, VerifiedLink> verifiedMappings =
			loadVerifiedMappings();
		ApartmentTradeScopeClassifier classifier =
			new ApartmentTradeScopeClassifier(
				referenceReader.read(referencePath),
				mappingReader.read(mappingPath)
			);
		long afterId = 0;
		long scanned = 0;
		long inScope = 0;
		long outOfScope = 0;
		long unknown = 0;
		long linked = 0;
		long zoneMismatch = 0;
		Map<Status, Long> assessments = new EnumMap<>(Status.class);

		while (true) {
			long cursor = afterId;
			BatchResult batch = transactionTemplate.execute(
				ignored -> linkBatch(cursor, classifier, verifiedMappings)
			);
			if (batch == null || batch.scanned() == 0) {
				break;
			}
			afterId = batch.lastId();
			scanned += batch.scanned();
			inScope += batch.inScope();
			outOfScope += batch.outOfScope();
			unknown += batch.unknown();
			linked += batch.linked();
			zoneMismatch += batch.zoneMismatch();
			batch.assessments().forEach((status, count) ->
				assessments.merge(status, count, Long::sum)
			);
		}
		return new Result(detached == null ? 0 : detached,
			scanned, inScope, outOfScope, unknown,
			linked, zoneMismatch, Map.copyOf(assessments));
	}

	private BatchResult linkBatch(
		long afterId,
		ApartmentTradeScopeClassifier classifier,
		Map<SourceKey, VerifiedLink> verifiedMappings
	) {
		List<ApartmentTrade> trades = tradeRepository
			.findByIdGreaterThanAndComplexIsNullAndZoneIsNullOrderByIdAsc(
				afterId, PageRequest.of(0, BATCH_SIZE)
			);
		if (trades.isEmpty()) {
			return new BatchResult(afterId, 0, 0, 0, 0, 0, 0, Map.of());
		}

		List<ApartmentTradeScope> scopes = new ArrayList<>(trades.size());
		Set<String> legalDongCodes = new HashSet<>();
		int inScope = 0;
		int outOfScope = 0;
		int unknown = 0;
		for (ApartmentTrade trade : trades) {
			ApartmentTradeScope scope = classifier.classify(
				trade.getDistrictCode(), trade.getLegalDongName()
			);
			scopes.add(scope);
			switch (scope.status()) {
				case IN_SCOPE -> {
					inScope++;
					legalDongCodes.add(scope.legalDongCode());
				}
				case OUT_OF_SCOPE -> outOfScope++;
				case UNKNOWN_LEGAL_DONG -> unknown++;
			}
		}

		Map<String, List<Complex>> complexesByLegalDong =
			loadComplexes(legalDongCodes);
		Map<Status, Long> assessments = new EnumMap<>(Status.class);
		int linked = 0;
		int zoneMismatch = 0;
		for (int index = 0; index < trades.size(); index++) {
			ApartmentTradeScope scope = scopes.get(index);
			if (scope.status() != ApartmentTradeScope.Status.IN_SCOPE) {
				continue;
			}
			ApartmentTrade trade = trades.get(index);
			VerifiedLink verified = verifiedMappings.get(SourceKey.of(trade));
			if (verified != null) {
				Complex verifiedComplex = complexesByLegalDong
					.getOrDefault(scope.legalDongCode(), List.of())
					.stream()
					.filter(value -> value.getId().equals(
						verified.complexId()))
					.findFirst().orElse(null);
				if (verifiedComplex != null
					&& !verifiedComplex.isRentalOnly()
					&& verifiedComplex.getZone() != null
					&& verifiedComplex.getZone().getId().equals(
						verified.zoneId())) {
					if (trade.linkToComplex(verifiedComplex)) {
						linked++;
					}
					assessments.merge(Status.VERIFIED_SOURCE_KEY, 1L,
						Long::sum);
					continue;
				}
			}
			List<Complex> candidates = candidateFinder.findCandidates(
				trade, scope,
				complexesByLegalDong.getOrDefault(
					scope.legalDongCode(), List.of()
				)
			);
			var assessment = assessmentService.assess(trade, candidates);
			Status status = assessment.status();
			assessments.merge(status, 1L, Long::sum);
			if (status != Status.NAME_EXACT
				&& status != Status.NAME_APPROVED_ALIAS) {
				continue;
			}
			String selectedId = assessment.candidateIds().getFirst();
			Complex candidate = candidates.stream()
				.filter(value -> value.getId().equals(selectedId))
				.findFirst()
				.orElseThrow(() -> new IllegalStateException(
					"후보 판정의 단지 ID가 후보 목록에 없습니다: "
						+ selectedId
				));
			if (candidate.getZone() == null) {
				zoneMismatch++;
				continue;
			}
			if (trade.linkToComplex(candidate)) {
				linked++;
			}
		}
		return new BatchResult(trades.getLast().getId(), trades.size(),
			inScope, outOfScope, unknown, linked, zoneMismatch,
			Map.copyOf(assessments));
	}

	private Map<String, List<Complex>> loadComplexes(
		Collection<String> legalDongCodes
	) {
		Map<String, List<Complex>> byCode = new HashMap<>();
		if (!legalDongCodes.isEmpty()) {
			for (Complex complex : complexRepository
				.findByLegalDongCodeIn(legalDongCodes)) {
				byCode.computeIfAbsent(complex.getLegalDongCode(),
					ignored -> new ArrayList<>()).add(complex);
			}
		}
		return byCode;
	}

	private Map<SourceKey, VerifiedLink> loadVerifiedMappings() {
		Map<SourceKey, VerifiedLink> mappings = new HashMap<>();
		for (Object[] row : tradeRepository.findVerifiedSourceMappings()) {
			Short buildYear = row[4] == null ? null
				: ((Number)row[4]).shortValue();
			SourceKey key = new SourceKey((String)row[0], (String)row[1],
				(String)row[2], (String)row[3], buildYear);
			mappings.put(key, new VerifiedLink((String)row[5],
				(String)row[6]));
		}
		return Map.copyOf(mappings);
	}

	private record SourceKey(String districtCode, String legalDongName,
		String lotNumber, String rawApartmentName, Short buildYear) {
		static SourceKey of(ApartmentTrade trade) {
			return new SourceKey(trade.getDistrictCode(),
				trade.getLegalDongName(), trade.getLotNumber(),
				trade.getRawApartmentName(), trade.getBuildYear());
		}
	}

	private record VerifiedLink(String complexId, String zoneId) {
	}

	public record Result(
		long detachedRentalLinks,
		long scanned,
		long inScope,
		long outOfScope,
		long unknown,
		long linked,
		long zoneMismatch,
		Map<Status, Long> assessments
	) {
	}

	private record BatchResult(
		long lastId,
		int scanned,
		int inScope,
		int outOfScope,
		int unknown,
		int linked,
		int zoneMismatch,
		Map<Status, Long> assessments
	) {
	}
}
