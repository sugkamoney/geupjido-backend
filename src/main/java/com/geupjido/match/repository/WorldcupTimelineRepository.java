package com.geupjido.match.repository;

import java.util.List;

import com.geupjido.match.model.WorldcupTimelineVote;

public interface WorldcupTimelineRepository {
	List<WorldcupTimelineVote> recentVotes(String zoneId, int limit);
}
