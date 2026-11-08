package com.geupjido.match.service;

import java.util.UUID;

import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import com.geupjido.match.model.GeneratedMatchGame;
import com.geupjido.match.model.Matchup;
import com.geupjido.match.model.RecordedVote;
import com.geupjido.match.model.WorldcupGameState;
import com.geupjido.match.repository.WorldcupGameRepository;

@Service
public class WorldcupGameService {

	private final MatchGenerationService matchGenerationService;
	private final WorldcupGameRepository gameRepository;
	private final WorldcupGameStartPersistenceService startPersistenceService;
	private final WorldcupDailyGameLimit dailyGameLimit;

	public WorldcupGameService(
		MatchGenerationService matchGenerationService,
		WorldcupGameRepository gameRepository,
		WorldcupGameStartPersistenceService startPersistenceService,
		WorldcupDailyGameLimit dailyGameLimit
	) {
		this.matchGenerationService = matchGenerationService;
		this.gameRepository = gameRepository;
		this.startPersistenceService = startPersistenceService;
		this.dailyGameLimit = dailyGameLimit;
	}

	public WorldcupGameStartResult startFromBanner(UUID anonymousId) {
		var latest = gameRepository.findLatestByAnonymousId(anonymousId);
		if (latest.isPresent() && !latest.get().completed()) {
			return new WorldcupGameStartResult(latest.get(), false);
		}
		dailyGameLimit.ensureCanStart(anonymousId);
		GeneratedMatchGame game = matchGenerationService.generateFromBanner();
		return startPersistenceService.saveOrResume(anonymousId, game);
	}

	public WorldcupGameStartResult startForZone(UUID anonymousId, String zoneId) {
		var latest = gameRepository.findLatestByAnonymousId(anonymousId);
		if (latest.isPresent() && !latest.get().completed()) {
			return new WorldcupGameStartResult(latest.get(), false);
		}
		dailyGameLimit.ensureCanStart(anonymousId);
		GeneratedMatchGame game = matchGenerationService.generateForZone(zoneId);
		return startPersistenceService.saveOrResume(anonymousId, game);
	}

	@Transactional(readOnly = true)
	public WorldcupGameState findCurrent(UUID anonymousId) {
		return gameRepository.findLatestByAnonymousId(anonymousId)
			.orElseThrow(WorldcupGameNotFoundException::new);
	}

	@Transactional
	public WorldcupGameState select(UUID anonymousId, int roundNumber, String selectedComplexId) {
		if (roundNumber < 1 || roundNumber > 10 || selectedComplexId == null
			|| selectedComplexId.isBlank()) {
			throw new WorldcupVoteRejectedException("INVALID_VOTE", "판 번호와 선택한 단지를 확인해 주세요.");
		}
		gameRepository.lockAnonymousId(anonymousId);
		WorldcupGameState state = gameRepository.findLatestByAnonymousIdForUpdate(anonymousId)
			.orElseThrow(WorldcupGameNotFoundException::new);
		Matchup matchup = state.game().matchups().get(roundNumber - 1);
		String leftId = matchup.left().complexId();
		String rightId = matchup.right().complexId();
		if (!selectedComplexId.equals(leftId) && !selectedComplexId.equals(rightId)) {
			throw new WorldcupVoteRejectedException("INVALID_VOTE", "이 판의 후보 단지를 선택해 주세요.");
		}
		for (RecordedVote vote : state.votes()) {
			if (vote.roundNumber() == roundNumber) {
				if (vote.selectedComplexId().equals(selectedComplexId)) {
					return state;
				}
				throw new WorldcupVoteRejectedException(
					"VOTE_ALREADY_RECORDED", "이미 확정한 선택은 바꿀 수 없습니다.");
			}
		}
		if (state.completed()) {
			throw new WorldcupVoteRejectedException("GAME_COMPLETED", "이미 완료한 게임입니다.");
		}
		if (roundNumber != state.nextRoundNumber()) {
			throw new WorldcupVoteRejectedException("VOTE_OUT_OF_ORDER", "다음 판부터 순서대로 선택해 주세요.");
		}
		gameRepository.saveVote(state.id(), roundNumber, leftId, rightId, selectedComplexId);
		if (roundNumber == 10) {
			gameRepository.complete(state.id());
		}
		return gameRepository.findLatestByAnonymousId(anonymousId)
			.orElseThrow(WorldcupGameNotFoundException::new);
	}
}
