package com.geupjido.match.service;

import java.util.Comparator;
import java.util.List;
import java.util.Map;
import java.util.function.Function;
import java.util.stream.Collectors;

import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Isolation;
import org.springframework.transaction.annotation.Transactional;

import com.geupjido.match.model.MatchZoneCandidate;
import com.geupjido.match.model.WorldcupZoneSelection;
import com.geupjido.match.repository.MatchCandidateRepository;
import com.geupjido.match.repository.WorldcupZoneSelectionRepository;
import com.geupjido.match.repository.WorldcupZoneSelectionRepository.ZoneRow;

@Service
public class WorldcupZoneSelectionService {

	private final WorldcupZoneSelectionRepository repository;
	private final MatchCandidateRepository candidateRepository;
	private final WorldcupMatchAvailability availability;

	public WorldcupZoneSelectionService(
		WorldcupZoneSelectionRepository repository,
		MatchCandidateRepository candidateRepository,
		WorldcupMatchAvailability availability
	) {
		this.repository = repository;
		this.candidateRepository = candidateRepository;
		this.availability = availability;
	}

	@Transactional(readOnly = true, isolation = Isolation.REPEATABLE_READ)
	public List<WorldcupZoneSelection> list() {
		List<MatchZoneCandidate> candidates = candidateRepository.findActiveZones();
		Map<String, MatchZoneCandidate> byId = candidates.stream()
			.collect(Collectors.toMap(MatchZoneCandidate::zoneId, Function.identity()));
		return repository.findAll().stream()
			.map(row -> selection(row, byId, candidates))
			.toList();
	}

	@Transactional(readOnly = true, isolation = Isolation.REPEATABLE_READ)
	public List<WorldcupZoneSelection> alternatives(String zoneId) {
		List<MatchZoneCandidate> candidates = candidateRepository.findActiveZones();
		Map<String, MatchZoneCandidate> byId = candidates.stream()
			.collect(Collectors.toMap(MatchZoneCandidate::zoneId, Function.identity()));
		Map<String, WorldcupZoneSelection> selections = repository.findAll().stream()
			.map(row -> selection(row, byId, candidates))
			.collect(Collectors.toMap(WorldcupZoneSelection::zoneId, Function.identity()));
		WorldcupZoneSelection selected = selections.get(zoneId);
		if (selected == null) {
			throw new WorldcupZoneSelectionNotFoundException();
		}
		if (selected.canStart()) {
			return List.of();
		}
		return repository.findAdjacentZoneIds(zoneId).stream()
			.map(selections::get)
			.filter(zone -> zone != null && zone.canStart())
			.sorted(Comparator.comparing(WorldcupZoneSelection::cityName)
				.thenComparing(WorldcupZoneSelection::zoneName)
				.thenComparing(WorldcupZoneSelection::zoneId))
			.toList();
	}

	private WorldcupZoneSelection selection(
		ZoneRow row, Map<String, MatchZoneCandidate> byId,
		List<MatchZoneCandidate> candidates
	) {
		MatchZoneCandidate candidate = byId.get(row.zoneId());
		boolean canStart = candidate != null
			&& availability.tierRange(candidate, candidates).isPresent();
		return new WorldcupZoneSelection(
			row.zoneId(), row.cityName(), row.zoneName(), row.tier(),
			row.activeComplexCount(), canStart
		);
	}
}
