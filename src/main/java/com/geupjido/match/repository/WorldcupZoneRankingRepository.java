package com.geupjido.match.repository;

import java.util.Optional;

import com.geupjido.match.model.WorldcupZoneRanking;

public interface WorldcupZoneRankingRepository {

	Optional<WorldcupZoneRanking> findByZoneId(String zoneId);
}
