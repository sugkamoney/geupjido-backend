package com.geupjido.match.service;

import java.util.Optional;
import java.util.UUID;

import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import com.geupjido.match.model.GeneratedMatchGame;
import com.geupjido.match.model.Matchup;
import com.geupjido.match.model.RecordedSkip;
import com.geupjido.match.model.RecordedVote;
import com.geupjido.match.model.WorldcupGameState;
import com.geupjido.match.model.WorldcupGameMode;
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
		var latest = latest(anonymousId, WorldcupGameMode.GENERAL, null, false);
		if (latest.isPresent() && !latest.get().completed()) {
			return new WorldcupGameStartResult(latest.get(), false);
		}
		dailyGameLimit.ensureCanStart(anonymousId);
		GeneratedMatchGame game = matchGenerationService.generateFromBanner();
		return startPersistenceService.saveOrResume(anonymousId, game);
	}

	public WorldcupGameStartResult startForZone(UUID anonymousId, String zoneId) {
		var latest = latest(anonymousId, WorldcupGameMode.ZONE_COMPLEX, zoneId, false);
		if (latest.isPresent() && !latest.get().completed()) {
			return new WorldcupGameStartResult(latest.get(), false);
		}
		GeneratedMatchGame game = matchGenerationService.generateWithinZone(zoneId);
		return startPersistenceService.saveOrResume(anonymousId, game, WorldcupGameMode.ZONE_COMPLEX);
	}

	@Transactional(readOnly = true)
	public WorldcupGameState findCurrent(UUID anonymousId) {
		return latest(anonymousId, WorldcupGameMode.GENERAL, null, false)
			.orElseThrow(WorldcupGameNotFoundException::new);
	}

	@Transactional(readOnly = true)
	public WorldcupGameState findZoneCurrent(UUID anonymousId, String zoneId) {
		return latest(anonymousId, WorldcupGameMode.ZONE_COMPLEX, zoneId, false)
			.orElseThrow(WorldcupGameNotFoundException::new);
	}

	@Transactional
	public WorldcupGameState select(UUID anonymousId, int roundNumber, String selectedComplexId) {
		return select(anonymousId, WorldcupGameMode.GENERAL, null, roundNumber, selectedComplexId);
	}

	@Transactional
	public WorldcupGameState selectZone(UUID anonymousId, String zoneId,
		int roundNumber, String selectedComplexId) {
		return select(anonymousId, WorldcupGameMode.ZONE_COMPLEX, zoneId,
			roundNumber, selectedComplexId);
	}

	private WorldcupGameState select(UUID anonymousId, WorldcupGameMode mode, String zoneId,
		int roundNumber, String selectedComplexId) {
		if (roundNumber < 1 || roundNumber > 10 || selectedComplexId == null
			|| selectedComplexId.isBlank()) {
			throw new WorldcupVoteRejectedException("INVALID_VOTE", "판 번호와 선택한 단지를 확인해 주세요.");
		}
		gameRepository.lockAnonymousId(anonymousId);
		WorldcupGameState state = latest(anonymousId, mode, zoneId, true)
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
		for (RecordedSkip skip : state.skips()) {
			if (skip.roundNumber() == roundNumber) {
				throw new WorldcupVoteRejectedException(
					"ROUND_ALREADY_SKIPPED", "건너뛴 판은 다시 선택할 수 없습니다.");
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
		return latest(anonymousId, mode, zoneId, false)
			.orElseThrow(WorldcupGameNotFoundException::new);
	}

	@Transactional
	public WorldcupGameState skip(UUID anonymousId, int roundNumber) {
		return skip(anonymousId, WorldcupGameMode.GENERAL, null, roundNumber);
	}

	@Transactional
	public WorldcupGameState skipZone(UUID anonymousId, String zoneId, int roundNumber) {
		return skip(anonymousId, WorldcupGameMode.ZONE_COMPLEX, zoneId, roundNumber);
	}

	private WorldcupGameState skip(UUID anonymousId, WorldcupGameMode mode,
		String zoneId, int roundNumber) {
		if (roundNumber < 1 || roundNumber > 10) {
			throw new WorldcupVoteRejectedException("INVALID_ROUND", "판 번호를 확인해 주세요.");
		}
		gameRepository.lockAnonymousId(anonymousId);
		WorldcupGameState state = latest(anonymousId, mode, zoneId, true)
			.orElseThrow(WorldcupGameNotFoundException::new);
		for (RecordedSkip skip : state.skips()) {
			if (skip.roundNumber() == roundNumber) {
				return state;
			}
		}
		for (RecordedVote vote : state.votes()) {
			if (vote.roundNumber() == roundNumber) {
				throw new WorldcupVoteRejectedException(
					"VOTE_ALREADY_RECORDED", "이미 선택한 판은 건너뛸 수 없습니다.");
			}
		}
		if (state.completed()) {
			throw new WorldcupVoteRejectedException("GAME_COMPLETED", "이미 완료한 게임입니다.");
		}
		if (roundNumber != state.nextRoundNumber()) {
			throw new WorldcupVoteRejectedException("VOTE_OUT_OF_ORDER", "다음 판부터 순서대로 진행해 주세요.");
		}
		gameRepository.saveSkip(state.id(), roundNumber);
		if (roundNumber == 10) {
			gameRepository.complete(state.id());
		}
		return latest(anonymousId, mode, zoneId, false)
			.orElseThrow(WorldcupGameNotFoundException::new);
	}

	private Optional<WorldcupGameState> latest(UUID anonymousId,
		WorldcupGameMode mode, String zoneId, boolean forUpdate) {
		WorldcupDailyGameLimit.Day day = mode == WorldcupGameMode.GENERAL
			? dailyGameLimit.currentDay() : null;
		return gameRepository.findLatest(anonymousId, mode, zoneId,
			day == null ? null : day.fromInclusive(),
			day == null ? null : day.toExclusive(), forUpdate);
	}
}
