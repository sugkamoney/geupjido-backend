package com.geupjido.trade.repository;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.List;
import java.util.Optional;

import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import com.geupjido.trade.domain.ApartmentTrade;

/**
 * 아파트 매매 실거래가의 저장과 조회를 담당한다.
 */
public interface ApartmentTradeRepository
	extends JpaRepository<ApartmentTrade, Long> {

	/** 이미 연결된 거래에서 원본 식별 조합이 한 단지·권역만 가리키는 경우만 반환한다. */
	@Query(value = """
		SELECT t.district_code, t.legal_dong_name, t.lot_number,
		       t.raw_apartment_name, t.build_year,
		       MIN(t.complex_id), MIN(t.zone_id)
		FROM apartment_trade t
		WHERE t.complex_id IS NOT NULL AND t.zone_id IS NOT NULL
		GROUP BY t.district_code, t.legal_dong_name, t.lot_number,
		         t.raw_apartment_name, t.build_year
		HAVING COUNT(DISTINCT t.complex_id) = 1
		   AND COUNT(DISTINCT t.zone_id) = 1
		""", nativeQuery = true)
	List<Object[]> findVerifiedSourceMappings();

	/** 확정된 임대 전용 단지의 과거 자동 연결만 해제하고 원본 거래는 보존한다. */
	@Modifying
	@Query(value = """
		UPDATE apartment_trade t
		SET complex_id = NULL, zone_id = NULL
		FROM complex c
		WHERE t.complex_id = c.id
		  AND c.sale_type LIKE '%임대%'
		  AND c.sale_type NOT LIKE '%분양%'
		""", nativeQuery = true)
	int detachRentalOnlyLinks();

	/** 삭제할 단지의 거래 원본은 두고 단지·권역 연결만 함께 해제한다. */
	@Modifying
	@Query(value = """
		UPDATE apartment_trade
		SET complex_id = NULL, zone_id = NULL
		WHERE complex_id = :complexId
		""", nativeQuery = true)
	int detachLinksForComplex(@Param("complexId") String complexId);

	@Query(value = """
		SELECT t.* FROM apartment_trade t
		WHERE t.id > :afterId
		  AND t.complex_id IS NULL
		  AND t.zone_id IS NULL
		  AND NOT EXISTS (
		      SELECT 1 FROM apartment_trade_conflict conflict
		      WHERE conflict.existing_trade_id = t.id
		        AND conflict.review_status = 'OPEN'
		  )
		ORDER BY t.id ASC
		""", nativeQuery = true)
	List<ApartmentTrade> findByIdGreaterThanAndComplexIsNullAndZoneIsNullOrderByIdAsc(
		@Param("afterId") Long afterId,
		Pageable pageable
	);

	@Query(value = """
		SELECT t.*
		FROM apartment_trade t
		JOIN complex c ON c.id = t.complex_id
		WHERE t.complex_id IN :complexIds
		  AND t.zone_id = c.zone_id
		  AND (c.sale_type IS NULL OR c.sale_type NOT LIKE '%임대%'
		       OR c.sale_type LIKE '%분양%')
		  AND t.is_canceled = false
		  AND t.dealing_type = '중개거래'
		  AND t.exclusive_area >= :areaStart
		  AND t.exclusive_area < :areaEnd
		  AND NOT EXISTS (
		      SELECT 1 FROM apartment_trade_conflict conflict
		      WHERE conflict.existing_trade_id = t.id
		        AND conflict.review_status = 'OPEN'
		  )
		ORDER BY t.complex_id, t.deal_date DESC,
		         t.deal_amount ASC, t.id ASC
		""", nativeQuery = true)
	List<ApartmentTrade> findRepresentativeCandidates(
		@Param("complexIds") List<String> complexIds,
		@Param("areaStart") BigDecimal areaStart,
		@Param("areaEnd") BigDecimal areaEnd
	);

	@Query(value = """
		SELECT t.*
		FROM apartment_trade t
		JOIN complex c ON c.id = t.complex_id
		WHERE t.complex_id IN :complexIds
		  AND t.zone_id = c.zone_id
		  AND (c.sale_type IS NULL OR c.sale_type NOT LIKE '%임대%'
		       OR c.sale_type LIKE '%분양%')
		  AND t.is_canceled = false
		  AND t.dealing_type = '중개거래'
		  AND t.exclusive_area >= :areaStart
		  AND t.exclusive_area <= :areaEnd
		  AND NOT EXISTS (
		      SELECT 1 FROM apartment_trade_conflict conflict
		      WHERE conflict.existing_trade_id = t.id
		        AND conflict.review_status = 'OPEN'
		  )
		ORDER BY t.complex_id, t.deal_date DESC,
		         t.deal_amount ASC, t.id ASC
		""", nativeQuery = true)
	List<ApartmentTrade> findFallbackRepresentativeCandidates(
		@Param("complexIds") List<String> complexIds,
		@Param("areaStart") BigDecimal areaStart,
		@Param("areaEnd") BigDecimal areaEnd
	);

	@Query(value = """
		SELECT t.*
		FROM apartment_trade t
		JOIN complex c ON c.id = t.complex_id
		WHERE t.complex_id IN :complexIds
		  AND t.zone_id = c.zone_id
		  AND (c.sale_type IS NULL OR c.sale_type NOT LIKE '%임대%'
		       OR c.sale_type LIKE '%분양%')
		  AND t.is_canceled = false
		  AND t.dealing_type = '직거래'
		  AND t.exclusive_area >= :areaStart
		  AND (t.exclusive_area < :areaEnd
		       OR (:includeAreaEnd = true AND t.exclusive_area = :areaEnd))
		  AND NOT EXISTS (
		      SELECT 1 FROM apartment_trade_conflict conflict
		      WHERE conflict.existing_trade_id = t.id
		        AND conflict.review_status = 'OPEN'
		  )
		ORDER BY t.complex_id, t.deal_date DESC,
		         t.deal_amount ASC, t.id ASC
		""", nativeQuery = true)
	List<ApartmentTrade> findDirectRepresentativeCandidates(
		@Param("complexIds") List<String> complexIds,
		@Param("areaStart") BigDecimal areaStart,
		@Param("areaEnd") BigDecimal areaEnd,
		@Param("includeAreaEnd") boolean includeAreaEnd
	);

	@Query(value = """
		SELECT t.*
		FROM apartment_trade t
		JOIN complex c ON c.id = t.complex_id
		WHERE t.complex_id IN :complexIds
		  AND t.zone_id = c.zone_id
		  AND (c.sale_type IS NULL OR c.sale_type NOT LIKE '%임대%'
		       OR c.sale_type LIKE '%분양%')
		  AND t.is_canceled = false
		  AND t.dealing_type = :dealingType
		  AND t.exclusive_area > 0
		  AND (t.exclusive_area < 44 OR t.exclusive_area > 65)
		  AND NOT EXISTS (
		      SELECT 1 FROM apartment_trade_conflict conflict
		      WHERE conflict.existing_trade_id = t.id
		        AND conflict.review_status = 'OPEN'
		  )
		ORDER BY t.complex_id, ABS(t.exclusive_area - 59) ASC,
		         t.deal_date DESC, t.deal_amount ASC, t.id ASC
		""", nativeQuery = true)
	List<ApartmentTrade> findOtherAreaRepresentativeCandidates(
		@Param("complexIds") List<String> complexIds,
		@Param("dealingType") String dealingType
	);

	List<ApartmentTrade>
	findByDistrictCodeAndLegalDongNameAndRawApartmentNameAndLotNumberAndBuildYear(
		String districtCode,
		String legalDongName,
		String rawApartmentName,
		String lotNumber,
		Short buildYear
	);

	List<ApartmentTrade>
	findByDistrictCodeAndLegalDongNameAndRawApartmentNameAndLotNumberAndBuildYearAndDealDateGreaterThanEqualAndDealDateLessThan(
		String districtCode,
		String legalDongName,
		String rawApartmentName,
		String lotNumber,
		Short buildYear,
		LocalDate startDate,
		LocalDate endDate
	);

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
