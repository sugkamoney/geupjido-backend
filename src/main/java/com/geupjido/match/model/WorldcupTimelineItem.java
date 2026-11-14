package com.geupjido.match.model;

import java.time.Instant;

import com.geupjido.match.model.WorldcupTimelineVote.Candidate;

public record WorldcupTimelineItem(
	String displayName,
	boolean mine,
	Instant selectedAt,
	Candidate left,
	Candidate right,
	String selectedComplexId
) {
}
