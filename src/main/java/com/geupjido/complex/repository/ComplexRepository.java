package com.geupjido.complex.repository;

import org.springframework.data.jpa.repository.JpaRepository;

import com.geupjido.complex.domain.Complex;

/**
 * 공동주택 단지의 저장과 조회를 담당한다.
 */
public interface ComplexRepository
	extends JpaRepository<Complex, String> {
}
