package com.geupjido.match.repository;

import java.util.List;

import com.geupjido.match.service.WorldcupVoteRatingCalculator.ComplexSeed;
import com.geupjido.match.service.WorldcupVoteRatingCalculator.ZoneSeed;

public interface WorldcupVoteRatingSeedRepository {

	Seeds loadSeeds();

	record Seeds(List<ZoneSeed> zones, List<ComplexSeed> complexes) {
		public Seeds {
			zones = List.copyOf(zones);
			complexes = List.copyOf(complexes);
		}
	}
}
