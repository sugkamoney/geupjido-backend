package com.geupjido.match.service;

import java.math.BigDecimal;
import java.util.ArrayList;
import java.util.Collections;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Random;
import java.util.Set;
import java.util.stream.Collectors;

import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Isolation;
import org.springframework.transaction.annotation.Transactional;

import com.geupjido.match.model.GeneratedMatchGame;
import com.geupjido.match.model.MatchCard;
import com.geupjido.match.model.MatchComplexCandidate;
import com.geupjido.match.model.MatchType;
import com.geupjido.match.model.MatchZoneCandidate;
import com.geupjido.match.model.Matchup;
import com.geupjido.match.repository.MatchCandidateRepository;

/** 활성 단지와 공개 급지의 한 시점 스냅샷에서 월드컵 10판을 생성한다. */
@Service
public class MatchGenerationService {

	private static final BigDecimal DEFAULT_TIER_RANGE = new BigDecimal("0.2");
	private static final BigDecimal EXTENDED_TIER_RANGE = new BigDecimal("0.3");
	private static final int GAME_SIZE = 10;
	private static final int WITHIN_ZONE_TARGET = 4;
	private static final double SEOUL_CHANCE = 0.6;
	private static final double EARTH_RADIUS_KM = 6371.0088;
	private static final double DISTANCE_SCALE_KM = 10.0;
	private static final double TIER_GAP_SCALE = 5.0;

	private final MatchCandidateRepository repository;
	private final Random random;

	@Autowired
	public MatchGenerationService(MatchCandidateRepository repository) {
		this(repository, new Random());
	}

	MatchGenerationService(MatchCandidateRepository repository, Random random) {
		this.repository = repository;
		this.random = random;
	}

	@Transactional(readOnly = true, isolation = Isolation.REPEATABLE_READ)
	public GeneratedMatchGame generateFromBanner() {
		List<MatchZoneCandidate> zones = repository.findActiveZones();
		MatchZoneCandidate baseZone = chooseBannerZone(zones);
		return generate(baseZone, zones);
	}

	@Transactional(readOnly = true, isolation = Isolation.REPEATABLE_READ)
	public GeneratedMatchGame generateForZone(String zoneId) {
		List<MatchZoneCandidate> zones = repository.findActiveZones();
		MatchZoneCandidate baseZone = zones.stream()
			.filter(zone -> zone.zoneId().equals(zoneId))
			.findFirst()
			.orElseThrow(() -> new MatchUnavailableException("투표할 활성 단지가 없는 권역입니다."));
		return generate(baseZone, zones);
	}

	private MatchZoneCandidate chooseBannerZone(List<MatchZoneCandidate> zones) {
		List<MatchZoneCandidate> seoul = zones.stream()
			.filter(zone -> zone.activeComplexCount() >= 4 && zone.cityId().startsWith("11"))
			.toList();
		List<MatchZoneCandidate> gyeonggiIncheon = zones.stream()
			.filter(zone -> zone.activeComplexCount() >= 4
				&& (zone.cityId().startsWith("41") || zone.cityId().startsWith("28")))
			.toList();
		if (seoul.isEmpty() && gyeonggiIncheon.isEmpty()) {
			throw new MatchUnavailableException("배너에서 시작할 수 있는 권역이 없습니다.");
		}
		List<MatchZoneCandidate> selectedGroup = random.nextDouble() < SEOUL_CHANCE
			? seoul : gyeonggiIncheon;
		if (selectedGroup.isEmpty()) {
			selectedGroup = seoul.isEmpty() ? gyeonggiIncheon : seoul;
		}
		return selectedGroup.get(random.nextInt(selectedGroup.size()));
	}

	private GeneratedMatchGame generate(
		MatchZoneCandidate baseZone,
		List<MatchZoneCandidate> zones
	) {
		List<MatchZoneCandidate> possibleOpponents = zones.stream()
			.filter(zone -> !zone.zoneId().equals(baseZone.zoneId()))
			.filter(zone -> tierGap(baseZone, zone).compareTo(EXTENDED_TIER_RANGE) <= 0)
			.toList();
		Set<String> zoneIds = possibleOpponents.stream()
			.map(MatchZoneCandidate::zoneId)
			.collect(Collectors.toCollection(HashSet::new));
		zoneIds.add(baseZone.zoneId());
		Map<String, List<MatchComplexCandidate>> complexesByZone = repository
			.findActiveComplexesByZoneIds(zoneIds).stream()
			.collect(Collectors.groupingBy(MatchComplexCandidate::zoneId));
		List<MatchComplexCandidate> baseComplexes =
			complexesByZone.getOrDefault(baseZone.zoneId(), List.of());
		if (baseComplexes.isEmpty()) {
			throw new MatchUnavailableException("기준 권역에 투표할 활성 단지가 없습니다.");
		}

		int withinCount = (int) Math.min(
			WITHIN_ZONE_TARGET,
			(long) baseComplexes.size() * (baseComplexes.size() - 1) / 2
		);
		int crossCount = GAME_SIZE - withinCount;
		BigDecimal tierRange = DEFAULT_TIER_RANGE;
		List<MatchZoneCandidate> opponents = eligibleOpponents(
			baseZone, possibleOpponents, complexesByZone, tierRange
		);
		if (availableCrossPairs(baseComplexes, opponents, complexesByZone) < crossCount) {
			tierRange = EXTENDED_TIER_RANGE;
			opponents = eligibleOpponents(
				baseZone, possibleOpponents, complexesByZone, tierRange
			);
		}
		if (availableCrossPairs(baseComplexes, opponents, complexesByZone) < crossCount) {
			throw new MatchUnavailableException("급지가 비슷한 단지로 10판을 만들 수 없습니다.");
		}

		Set<PairKey> usedPairs = new HashSet<>();
		Map<String, Integer> complexAppearances = new HashMap<>();
		List<Matchup> matchups = new ArrayList<>(GAME_SIZE);
		addWithinZoneMatches(baseComplexes, withinCount, usedPairs, complexAppearances, matchups);
		addCrossZoneMatches(
			baseZone, baseComplexes, opponents, complexesByZone, crossCount,
			usedPairs, complexAppearances, matchups
		);
		Collections.shuffle(matchups, random);
		return new GeneratedMatchGame(baseZone.zoneId(), tierRange, matchups);
	}

	private List<MatchZoneCandidate> eligibleOpponents(
		MatchZoneCandidate baseZone,
		List<MatchZoneCandidate> possibleOpponents,
		Map<String, List<MatchComplexCandidate>> complexesByZone,
		BigDecimal tierRange
	) {
		return possibleOpponents.stream()
			.filter(zone -> tierGap(baseZone, zone).compareTo(tierRange) <= 0)
			.filter(zone -> !complexesByZone.getOrDefault(zone.zoneId(), List.of()).isEmpty())
			.toList();
	}

	private long availableCrossPairs(
		List<MatchComplexCandidate> baseComplexes,
		List<MatchZoneCandidate> opponents,
		Map<String, List<MatchComplexCandidate>> complexesByZone
	) {
		return opponents.stream()
			.mapToLong(zone -> (long) baseComplexes.size()
				* complexesByZone.get(zone.zoneId()).size())
			.sum();
	}

	private void addWithinZoneMatches(
		List<MatchComplexCandidate> complexes,
		int target,
		Set<PairKey> usedPairs,
		Map<String, Integer> appearances,
		List<Matchup> matchups
	) {
		for (int match = 0; match < target; match++) {
			ComplexPair pair = chooseLeastShownPair(complexes, complexes, true, usedPairs, appearances);
			addMatch(pair, MatchType.WITHIN_ZONE, usedPairs, appearances, matchups);
		}
	}

	private void addCrossZoneMatches(
		MatchZoneCandidate baseZone,
		List<MatchComplexCandidate> baseComplexes,
		List<MatchZoneCandidate> opponents,
		Map<String, List<MatchComplexCandidate>> complexesByZone,
		int target,
		Set<PairKey> usedPairs,
		Map<String, Integer> appearances,
		List<Matchup> matchups
	) {
		Map<String, Integer> zoneAppearances = new HashMap<>();
		int preferredLimit = Math.max(2, (target + 2) / 3);
		for (int match = 0; match < target; match++) {
			List<MatchZoneCandidate> available = opponents.stream()
				.filter(zone -> zoneAppearances.getOrDefault(zone.zoneId(), 0)
					< (long) baseComplexes.size() * complexesByZone.get(zone.zoneId()).size())
				.toList();
			if (available.isEmpty()) {
				throw new MatchUnavailableException("서로 다른 단지 쌍이 부족합니다.");
			}
			long distinctUsed = zoneAppearances.size();
			if (distinctUsed < Math.min(3, opponents.size())) {
				List<MatchZoneCandidate> unused = available.stream()
					.filter(zone -> !zoneAppearances.containsKey(zone.zoneId()))
					.toList();
				if (!unused.isEmpty()) {
					available = unused;
				}
			}
			List<MatchZoneCandidate> underLimit = available.stream()
				.filter(zone -> zoneAppearances.getOrDefault(zone.zoneId(), 0) < preferredLimit)
				.toList();
			if (!underLimit.isEmpty()) {
				available = underLimit;
			}
			MatchZoneCandidate opponent = weightedPick(baseZone, available);
			ComplexPair pair = chooseLeastShownPair(
				baseComplexes, complexesByZone.get(opponent.zoneId()),
				false, usedPairs, appearances
			);
			addMatch(pair, MatchType.CROSS_ZONE, usedPairs, appearances, matchups);
			zoneAppearances.merge(opponent.zoneId(), 1, Integer::sum);
		}
	}

	private MatchZoneCandidate weightedPick(
		MatchZoneCandidate baseZone,
		List<MatchZoneCandidate> opponents
	) {
		double totalWeight = opponents.stream()
			.mapToDouble(zone -> weight(baseZone, zone))
			.sum();
		double draw = random.nextDouble() * totalWeight;
		for (MatchZoneCandidate zone : opponents) {
			draw -= weight(baseZone, zone);
			if (draw < 0) {
				return zone;
			}
		}
		return opponents.getLast();
	}

	private double weight(MatchZoneCandidate baseZone, MatchZoneCandidate opponent) {
		double gap = tierGap(baseZone, opponent).doubleValue();
		double distanceKm = distanceKm(baseZone, opponent);
		return 1.0 / (1.0 + TIER_GAP_SCALE * gap)
			/ (1.0 + distanceKm / DISTANCE_SCALE_KM);
	}

	private double distanceKm(MatchZoneCandidate first, MatchZoneCandidate second) {
		double latitudeDelta = Math.toRadians(second.latitude() - first.latitude());
		double longitudeDelta = Math.toRadians(second.longitude() - first.longitude());
		double a = Math.pow(Math.sin(latitudeDelta / 2), 2)
			+ Math.cos(Math.toRadians(first.latitude()))
				* Math.cos(Math.toRadians(second.latitude()))
				* Math.pow(Math.sin(longitudeDelta / 2), 2);
		return 2 * EARTH_RADIUS_KM * Math.asin(Math.min(1, Math.sqrt(a)));
	}

	private BigDecimal tierGap(MatchZoneCandidate first, MatchZoneCandidate second) {
		return first.tier().subtract(second.tier()).abs();
	}

	private ComplexPair chooseLeastShownPair(
		List<MatchComplexCandidate> firstGroup,
		List<MatchComplexCandidate> secondGroup,
		boolean withinZone,
		Set<PairKey> usedPairs,
		Map<String, Integer> appearances
	) {
		ComplexPair best = null;
		int bestMaximum = Integer.MAX_VALUE;
		int bestTotal = Integer.MAX_VALUE;
		int tiedCount = 0;
		for (int firstIndex = 0; firstIndex < firstGroup.size(); firstIndex++) {
			int secondStart = withinZone ? firstIndex + 1 : 0;
			for (int secondIndex = secondStart; secondIndex < secondGroup.size(); secondIndex++) {
				MatchComplexCandidate first = firstGroup.get(firstIndex);
				MatchComplexCandidate second = secondGroup.get(secondIndex);
				if (usedPairs.contains(PairKey.of(first.complexId(), second.complexId()))) {
					continue;
				}
				int firstShown = appearances.getOrDefault(first.complexId(), 0);
				int secondShown = appearances.getOrDefault(second.complexId(), 0);
				int maximum = Math.max(firstShown, secondShown);
				int total = firstShown + secondShown;
				if (maximum < bestMaximum || (maximum == bestMaximum && total < bestTotal)) {
					best = new ComplexPair(first, second);
					bestMaximum = maximum;
					bestTotal = total;
					tiedCount = 1;
				} else if (maximum == bestMaximum && total == bestTotal
					&& random.nextInt(++tiedCount) == 0) {
					best = new ComplexPair(first, second);
				}
			}
		}
		if (best == null) {
			throw new MatchUnavailableException("서로 다른 단지 쌍이 부족합니다.");
		}
		return best;
	}

	private void addMatch(
		ComplexPair pair,
		MatchType type,
		Set<PairKey> usedPairs,
		Map<String, Integer> appearances,
		List<Matchup> matchups
	) {
		usedPairs.add(PairKey.of(pair.first().complexId(), pair.second().complexId()));
		appearances.merge(pair.first().complexId(), 1, Integer::sum);
		appearances.merge(pair.second().complexId(), 1, Integer::sum);
		MatchCard firstCard = MatchCard.from(pair.first());
		MatchCard secondCard = MatchCard.from(pair.second());
		matchups.add(random.nextBoolean()
			? new Matchup(type, firstCard, secondCard)
			: new Matchup(type, secondCard, firstCard));
	}

	private record ComplexPair(MatchComplexCandidate first, MatchComplexCandidate second) {
	}

	private record PairKey(String lowerId, String higherId) {
		private static PairKey of(String firstId, String secondId) {
			return firstId.compareTo(secondId) < 0
				? new PairKey(firstId, secondId)
				: new PairKey(secondId, firstId);
		}
	}
}
