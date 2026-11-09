package com.geupjido.match.repository;

import java.util.Optional;

import com.geupjido.match.model.WorldcupPairRatio;

public interface WorldcupPairRatioRepository {

	Optional<WorldcupPairRatio> findVisibleRatio(String leftComplexId, String rightComplexId);
}
