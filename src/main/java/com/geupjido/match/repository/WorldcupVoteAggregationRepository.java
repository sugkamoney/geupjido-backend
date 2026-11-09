package com.geupjido.match.repository;

import java.util.function.Consumer;

import com.geupjido.match.model.WorldcupVoteForAggregation;

public interface WorldcupVoteAggregationRepository {

	void forEachRecordedVote(Consumer<WorldcupVoteForAggregation> consumer);
}
