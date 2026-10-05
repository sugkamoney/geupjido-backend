package com.geupjido.trade.repository;

import org.springframework.data.jpa.repository.JpaRepository;

import com.geupjido.trade.domain.ApartmentTrade;

/**
 * 아파트 매매 실거래가의 저장과 조회를 담당한다.
 */
public interface ApartmentTradeRepository
	extends JpaRepository<ApartmentTrade, Long> {
}
