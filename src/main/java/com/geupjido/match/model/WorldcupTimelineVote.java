package com.geupjido.match.model;

import java.time.Instant;
import java.util.UUID;

/** 저장된 한 판의 선택. 방문자 ID는 표시 이름과 본인 여부 계산에만 사용한다. */
public record WorldcupTimelineVote(
	UUID anonymousId,
	Instant selectedAt,
	Candidate left,
	Candidate right,
	String selectedComplexId
) {
	public record Candidate(String complexId, String name, String zoneId, String zoneName) {
	}
}
