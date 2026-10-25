package com.geupjido.trade.repository;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.Optional;

import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import com.geupjido.trade.domain.ApartmentTrade;

/**
 * 아파트 매매 실거래가의 저장과 조회를 담당한다.
 */
public interface ApartmentTradeRepository
	extends JpaRepository<ApartmentTrade, Long> {

	@Query(value = """
		SELECT *
		FROM apartment_trade
		WHERE district_code = :districtCode
		  AND legal_dong_name = :legalDongName
		  AND raw_apartment_name = :rawApartmentName
		  AND deal_date = :dealDate
		  AND exclusive_area IS NOT DISTINCT FROM :exclusiveArea
		  AND floor IS NOT DISTINCT FROM :floor
		  AND deal_amount = :dealAmount
		""", nativeQuery = true)
	Optional<ApartmentTrade> findBySourceKey(
		@Param("districtCode") String districtCode,
		@Param("legalDongName") String legalDongName,
		@Param("rawApartmentName") String rawApartmentName,
		@Param("dealDate") LocalDate dealDate,
		@Param("exclusiveArea") BigDecimal exclusiveArea,
		@Param("floor") Short floor,
		@Param("dealAmount") long dealAmount
	);
}
