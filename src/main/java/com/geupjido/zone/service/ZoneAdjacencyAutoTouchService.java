package com.geupjido.zone.service;

import java.util.List;

import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import com.geupjido.zone.repository.ZoneAdjacencyCandidateRepository;
import com.geupjido.zone.repository.ZoneAdjacencyCandidateRepository.ZoneIdPair;
import com.geupjido.zone.repository.ZoneAdjacencyImportRepository;

/**
 * 권역 경계에 맞춰 자동 접경 관계를 갱신한다.
 */
@Service
public class ZoneAdjacencyAutoTouchService {

	private final ZoneAdjacencyCandidateRepository candidateRepository;
	private final ZoneAdjacencyImportRepository importRepository;

	public ZoneAdjacencyAutoTouchService(
		ZoneAdjacencyCandidateRepository candidateRepository,
		ZoneAdjacencyImportRepository importRepository
	) {
		this.candidateRepository = candidateRepository;
		this.importRepository = importRepository;
	}

	@Transactional
	public void refreshTouchingZoneAdjacencies() {
		importRepository.deleteStaleAutoTouchRelations();
		List<ZoneIdPair> candidates = candidateRepository.findTouchingPairs();
		importRepository.insertAutoTouchPairs(candidates);
	}
}
