package com.geupjido.complex.repository;

import java.util.List;

import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import com.geupjido.complex.domain.Complex;

/**
 * 공동주택 단지의 저장과 조회를 담당한다.
 */
public interface ComplexRepository
	extends JpaRepository<Complex, String> {

	@Query("""
		SELECT c.id
		FROM Complex c
		WHERE c.id > :afterId
		  AND (c.location IS NULL OR c.zone IS NULL)
		ORDER BY c.id
		""")
	List<String> findIdsNeedingLocationOrZone(
		@Param("afterId") String afterId,
		Pageable pageable
	);

	List<Complex> findByLegalDongCode(String legalDongCode);
}
