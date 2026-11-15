package com.geupjido.ranking.repository;

import java.util.List;

import com.geupjido.ranking.model.RankingSource;
import com.geupjido.ranking.model.RankingView.Region;

public interface RankingRepository {
	RankingSource.Publication publication();

	List<RankingSource.Zone> zones(Region region);
}
