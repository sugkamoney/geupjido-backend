package com.geupjido.match.api;

import java.time.Instant;
import java.util.List;

import com.geupjido.match.model.WorldcupTimelineItem;
import com.geupjido.match.model.WorldcupTimelineVote;
public record WorldcupTimelineResponse(List<Entry> items) {

	public static WorldcupTimelineResponse from(List<WorldcupTimelineItem> items) {
		return new WorldcupTimelineResponse(items.stream().map(Entry::from).toList());
	}

	public record Entry(String displayName, boolean mine, Instant selectedAt,
		Candidate left, Candidate right, String selectedComplexId) {
		private static Entry from(WorldcupTimelineItem item) {
			return new Entry(item.displayName(), item.mine(), item.selectedAt(),
				Candidate.from(item.left()), Candidate.from(item.right()),
				item.selectedComplexId());
		}
	}

	public record Candidate(String complexId, String name, String zoneId, String zoneName) {
		private static Candidate from(WorldcupTimelineVote.Candidate candidate) {
			return new Candidate(candidate.complexId(), candidate.name(),
				candidate.zoneId(), candidate.zoneName());
		}
	}
}
