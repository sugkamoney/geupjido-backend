package com.geupjido.match.repository;

import java.util.Collection;
import java.util.List;

import com.geupjido.match.model.MatchComplexCandidate;
import com.geupjido.match.model.MatchZoneCandidate;

/** 대진 생성에 필요한 활성 권역·단지 후보만 조회한다. */
public interface MatchCandidateRepository {
	List<MatchZoneCandidate> findActiveZones();

	List<MatchComplexCandidate> findActiveComplexesByZoneIds(Collection<String> zoneIds);
}
