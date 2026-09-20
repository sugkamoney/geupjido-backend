package com.geupjido.zone.repository;

import com.geupjido.zone.domain.Zone;
import org.springframework.data.jpa.repository.JpaRepository;

/**
 * 급지 산정 단위인 권역을 저장하고 조회한다.
 */
public interface ZoneRepository extends JpaRepository<Zone, String> {
}
