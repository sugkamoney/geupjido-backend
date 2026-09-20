package com.geupjido.zone.repository;

import com.geupjido.zone.domain.ZoneAdjacency;
import com.geupjido.zone.domain.ZoneAdjacencyId;
import org.springframework.data.jpa.repository.JpaRepository;

/**
 * 권역 사이의 인접 관계를 저장하고 조회한다.
 */
public interface ZoneAdjacencyRepository extends JpaRepository<ZoneAdjacency, ZoneAdjacencyId> {
}
