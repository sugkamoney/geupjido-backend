package com.geupjido.trade.domain;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.regex.Pattern;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.FetchType;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.JoinColumn;
import jakarta.persistence.ManyToOne;
import jakarta.persistence.Table;

import org.hibernate.annotations.CreationTimestamp;

import com.geupjido.complex.domain.Complex;
import com.geupjido.zone.domain.Zone;

/**
 * 국토교통부 API에서 수집한 아파트 매매 실거래가 원본을 나타낸다.
 */
@Entity
@Table(name = "apartment_trade")
public class ApartmentTrade {

	private static final Pattern DISTRICT_CODE_PATTERN =
		Pattern.compile("[0-9]{5}");

	@Id
	@GeneratedValue(strategy = GenerationType.IDENTITY)
	private Long id;

	@ManyToOne(fetch = FetchType.LAZY)
	@JoinColumn(name = "complex_id")
	private Complex complex;

	@ManyToOne(fetch = FetchType.LAZY)
	@JoinColumn(name = "zone_id")
	private Zone zone;

	@Column(name = "deal_date", nullable = false)
	private LocalDate dealDate;

	@Column(name = "deal_amount", nullable = false)
	private long dealAmountTenThousandWon;

	@Column(
		name = "exclusive_area",
		precision = 8,
		scale = 2
	)
	private BigDecimal exclusiveArea;

	@Column(name = "floor")
	private Short floor;

	@Column(name = "build_year")
	private Short buildYear;

	@Column(
		name = "district_code",
		length = 5,
		nullable = false
	)
	private String districtCode;

	@Column(
		name = "legal_dong_name",
		length = 50,
		nullable = false
	)
	private String legalDongName;

	@Column(
		name = "raw_apartment_name",
		length = 100,
		nullable = false
	)
	private String rawApartmentName;

	@Column(name = "lot_number", length = 30)
	private String lotNumber;

	@Column(name = "apartment_dong", length = 50)
	private String apartmentDong;

	@Column(name = "dealing_type", length = 30)
	private String dealingType;

	@Column(name = "is_canceled", nullable = false)
	private boolean canceled;

	@Column(name = "cancellation_date")
	private LocalDate cancellationDate;

	@CreationTimestamp
	@Column(
		name = "created_at",
		nullable = false,
		updatable = false
	)
	private LocalDateTime createdAt;

	protected ApartmentTrade() {
	}

	public ApartmentTrade(
		LocalDate dealDate,
		long dealAmountTenThousandWon,
		BigDecimal exclusiveArea,
		Short floor,
		Short buildYear,
		String districtCode,
		String legalDongName,
		String rawApartmentName,
		String lotNumber,
		String apartmentDong,
		String dealingType,
		boolean canceled,
		LocalDate cancellationDate
	) {
		this.dealDate = requireDealDate(dealDate);
		this.dealAmountTenThousandWon =
			requirePositiveDealAmount(dealAmountTenThousandWon);
		this.exclusiveArea =
			requirePositiveExclusiveArea(exclusiveArea);
		this.floor = floor;
		this.buildYear = requirePositiveBuildYear(buildYear);
		this.districtCode = requireDistrictCode(districtCode);
		this.legalDongName =
			requireText(legalDongName, "법정동명");
		this.rawApartmentName =
			requireText(rawApartmentName, "아파트명");
		this.lotNumber = normalizeNullableText(lotNumber);
		this.apartmentDong =
			normalizeNullableText(apartmentDong);
		this.dealingType = normalizeNullableText(dealingType);
		validateCancellation(
			this.dealDate,
			canceled,
			cancellationDate
		);

		this.canceled = canceled;
		this.cancellationDate = cancellationDate;
	}

	private static String requireDistrictCode(String value) {
		if (
			value == null
				|| !DISTRICT_CODE_PATTERN.matcher(value).matches()
		) {
			throw new IllegalArgumentException(
				"시군구 코드는 숫자 5자리여야 합니다."
			);
		}

		return value;
	}

	private static String requireText(
		String value,
		String fieldName
	) {
		if (value == null || value.isBlank()) {
			throw new IllegalArgumentException(
				fieldName + "은(는) 비어 있을 수 없습니다."
			);
		}

		return value.trim();
	}

	private static String normalizeNullableText(String value) {
		if (value == null || value.isBlank()) {
			return null;
		}

		return value.trim();
	}

	private static LocalDate requireDealDate(LocalDate value) {
		if (value == null) {
			throw new IllegalArgumentException(
				"계약일은 비어 있을 수 없습니다."
			);
		}

		return value;
	}

	private static long requirePositiveDealAmount(long value) {
		if (value <= 0) {
			throw new IllegalArgumentException(
				"거래금액은 0보다 커야 합니다."
			);
		}

		return value;
	}

	private static BigDecimal requirePositiveExclusiveArea(
		BigDecimal value
	) {
		if (value != null && value.signum() <= 0) {
			throw new IllegalArgumentException(
				"전용면적은 0보다 커야 합니다."
			);
		}

		return value;
	}

	private static Short requirePositiveBuildYear(Short value) {
		if (value != null && value <= 0) {
			throw new IllegalArgumentException(
				"건축연도는 0보다 커야 합니다."
			);
		}

		return value;
	}

	private static void validateCancellation(
		LocalDate dealDate,
		boolean canceled,
		LocalDate cancellationDate
	) {
		if (canceled && cancellationDate == null) {
			throw new IllegalArgumentException(
				"해제된 거래에는 해제일이 필요합니다."
			);
		}

		if (!canceled && cancellationDate != null) {
			throw new IllegalArgumentException(
				"정상 거래에는 해제일이 있을 수 없습니다."
			);
		}

		if (
			cancellationDate != null
				&& cancellationDate.isBefore(dealDate)
		) {
			throw new IllegalArgumentException(
				"해제일은 계약일보다 빠를 수 없습니다."
			);
		}
	}

	public Long getId() {
		return id;
	}

	public Complex getComplex() {
		return complex;
	}

	public Zone getZone() {
		return zone;
	}

	public LocalDate getDealDate() {
		return dealDate;
	}

	public long getDealAmountTenThousandWon() {
		return dealAmountTenThousandWon;
	}

	public BigDecimal getExclusiveArea() {
		return exclusiveArea;
	}

	public Short getFloor() {
		return floor;
	}

	public Short getBuildYear() {
		return buildYear;
	}

	public String getDistrictCode() {
		return districtCode;
	}

	public String getLegalDongName() {
		return legalDongName;
	}

	public String getRawApartmentName() {
		return rawApartmentName;
	}

	public String getLotNumber() {
		return lotNumber;
	}

	public String getApartmentDong() {
		return apartmentDong;
	}

	public String getDealingType() {
		return dealingType;
	}

	public boolean isCanceled() {
		return canceled;
	}

	public LocalDate getCancellationDate() {
		return cancellationDate;
	}

	public LocalDateTime getCreatedAt() {
		return createdAt;
	}
}
