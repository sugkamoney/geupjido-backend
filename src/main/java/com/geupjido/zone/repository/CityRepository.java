package com.geupjido.zone.repository;

import com.geupjido.zone.domain.City;
import org.springframework.data.jpa.repository.JpaRepository;

/**
 * 시·구 단위 행정지역을 저장하고 조회한다.
 */
public interface CityRepository extends JpaRepository<City, String> {
}
