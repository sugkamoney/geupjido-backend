package com.geupjido.zone.repository;

import com.geupjido.zone.domain.Region;
import org.springframework.data.jpa.repository.JpaRepository;

/**
 * 최상위 지역 정보를 저장하고 조회한다.
 */
public interface RegionRepository extends JpaRepository<Region, String> {
}
