package com.geupjido.match.model;

import java.time.Instant;

/** 저장된 한 판의 확정 선택. */
public record RecordedVote(int roundNumber, String selectedComplexId, Instant selectedAt) {
}
