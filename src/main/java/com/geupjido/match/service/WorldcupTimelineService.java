package com.geupjido.match.service;

import java.util.List;
import java.util.UUID;

import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import com.geupjido.match.model.WorldcupTimelineItem;
import com.geupjido.match.model.WorldcupTimelineVote;
import com.geupjido.match.repository.WorldcupTimelineRepository;

@Service
public class WorldcupTimelineService {

	private static final int DISPLAY_LIMIT = 20;
	private static final String[] TREES = {
		"소나무", "은행나무", "느티나무", "단풍나무",
		"자작나무", "벚나무", "전나무", "버드나무",
		"감나무", "동백나무", "대나무", "측백나무",
		"살구나무", "목련", "이팝나무", "회화나무"
	};

	private final WorldcupTimelineRepository repository;

	public WorldcupTimelineService(WorldcupTimelineRepository repository) {
		this.repository = repository;
	}

	@Transactional(readOnly = true)
	public List<WorldcupTimelineItem> recent(String zoneId, UUID currentVisitorId) {
		String filter = zoneId == null || zoneId.isBlank() ? null : zoneId.trim();
		return repository.recentVotes(filter, DISPLAY_LIMIT).stream()
			.map(vote -> toItem(vote, currentVisitorId))
			.toList();
	}

	private WorldcupTimelineItem toItem(WorldcupTimelineVote vote, UUID currentVisitorId) {
		boolean mine = vote.anonymousId().equals(currentVisitorId);
		String displayName = mine ? "나" : "익명의 "
			+ TREES[Math.floorMod(vote.anonymousId().hashCode(), TREES.length)];
		return new WorldcupTimelineItem(displayName, mine, vote.selectedAt(),
			vote.left(), vote.right(), vote.selectedComplexId());
	}
}
