package com.geupjido.zone.repository;

import java.util.List;

import com.geupjido.zone.domain.Zone;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

/**
 * 급지 산정 단위인 권역을 저장하고 조회한다.
 */
public interface ZoneRepository extends JpaRepository<Zone, String> {
	@Query(value = """
    SELECT z.*
    FROM zone z
    WHERE ST_Covers(
        z.polygon,
        ST_SetSRID(ST_MakePoint(:longitude, :latitude), 4326)
    )
    """, nativeQuery = true)
	List<Zone> findCoveringZones(
		@Param("longitude") double longitude,
		@Param("latitude") double latitude
	);
}
