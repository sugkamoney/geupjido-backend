package com.geupjido.match.model;

import java.time.Instant;
import java.util.Objects;
import java.util.UUID;

/** 집계 시 한 표를 판별하는 데 필요한 게임·방문자·단지·권역 정보. */
public record WorldcupVoteForAggregation(
	long gameId,
	int roundNumber,
	UUID anonymousId,
	String leftComplexId,
	String rightComplexId,
	String selectedComplexId,
	String leftZoneId,
	String rightZoneId,
	Instant selectedAt
) {
	public WorldcupVoteForAggregation {
		if (gameId <= 0 || roundNumber < 1 || roundNumber > 10) {
			throw new IllegalArgumentException("올바른 게임·판 번호가 필요합니다.");
		}
		Objects.requireNonNull(anonymousId, "익명 방문자 ID가 필요합니다.");
		Objects.requireNonNull(selectedAt, "선택 시각이 필요합니다.");
		leftComplexId = requireText(leftComplexId, "왼쪽 단지 ID");
		rightComplexId = requireText(rightComplexId, "오른쪽 단지 ID");
		selectedComplexId = requireText(selectedComplexId, "선택 단지 ID");
		leftZoneId = requireText(leftZoneId, "왼쪽 권역 ID");
		rightZoneId = requireText(rightZoneId, "오른쪽 권역 ID");
		if (leftComplexId.equals(rightComplexId)
			|| (!selectedComplexId.equals(leftComplexId)
				&& !selectedComplexId.equals(rightComplexId))) {
			throw new IllegalArgumentException("선택한 단지는 대진의 두 단지 중 하나여야 합니다.");
		}
	}

	public boolean crossZone() {
		return !leftZoneId.equals(rightZoneId);
	}

	private static String requireText(String value, String label) {
		if (value == null || value.isBlank()) {
			throw new IllegalArgumentException(label + "가 필요합니다.");
		}
		return value;
	}
}
