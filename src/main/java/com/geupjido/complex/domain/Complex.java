package com.geupjido.complex.domain;

import java.math.BigDecimal;
import java.time.LocalDate;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.FetchType;
import jakarta.persistence.Id;
import jakarta.persistence.JoinColumn;
import jakarta.persistence.ManyToOne;
import jakarta.persistence.Table;

import org.locationtech.jts.geom.Point;

import com.geupjido.zone.domain.Zone;

/**
 * 외부 API에서 수집한 공동주택 단지와 위치·평가 정보를 나타낸다.
 */
@Entity
@Table(name = "complex")
public class Complex {

	@Id
	@Column(name = "id", length = 20, nullable = false)
	private String id;

	@ManyToOne(fetch = FetchType.LAZY)
	@JoinColumn(name = "zone_id")
	private Zone zone;

	@Column(name = "name", length = 100, nullable = false)
	private String name;

	@Column(name = "address_jibun", length = 200, nullable = false)
	private String addressJibun;

	@Column(name = "address_road", length = 200)
	private String addressRoad;

	@Column(
		name = "location",
		columnDefinition = "geometry(Point,4326)"
	)
	private Point location;

	@Column(name = "households", nullable = false)
	private Integer households;

	@Column(name = "building_count")
	private Integer buildingCount;

	@Column(name = "approval_date")
	private LocalDate approvalDate;

	@Column(name = "score", precision = 8, scale = 2)
	private BigDecimal score;

	@Column(name = "zone_rank")
	private Integer zoneRank;

	@Column(name = "price")
	private Integer price;

	@Column(
		name = "representative_area",
		precision = 6,
		scale = 2
	)
	private BigDecimal representativeArea;

	@Column(name = "last_deal_date")
	private LocalDate lastDealDate;

	@Column(name = "is_active", nullable = false)
	private boolean active;

	protected Complex() {
	}

	public Complex(
		String id,
		String name,
		String addressJibun,
		String addressRoad,
		int households,
		Integer buildingCount,
		LocalDate approvalDate
	) {
		this.id = requireText(id, "단지 코드");
		this.name = requireText(name, "단지명");
		this.addressJibun = requireText(addressJibun, "법정동 주소");
		this.addressRoad = addressRoad;

		if (households < 0) {
			throw new IllegalArgumentException(
				"세대수는 0 이상이어야 합니다."
			);
		}

		if (buildingCount != null && buildingCount <= 0) {
			throw new IllegalArgumentException(
				"동 수는 1 이상이어야 합니다."
			);
		}

		this.households = households;
		this.buildingCount = buildingCount;
		this.approvalDate = approvalDate;
		this.active = false;
	}

	public String getId() {
		return id;
	}

	public Zone getZone() {
		return zone;
	}

	public String getName() {
		return name;
	}

	public String getAddressJibun() {
		return addressJibun;
	}

	public String getAddressRoad() {
		return addressRoad;
	}

	public Point getLocation() {
		return location;
	}

	public Integer getHouseholds() {
		return households;
	}

	public Integer getBuildingCount() {
		return buildingCount;
	}

	public LocalDate getApprovalDate() {
		return approvalDate;
	}

	public BigDecimal getScore() {
		return score;
	}

	public Integer getZoneRank() {
		return zoneRank;
	}

	public Integer getPrice() {
		return price;
	}

	public BigDecimal getRepresentativeArea() {
		return representativeArea;
	}

	public LocalDate getLastDealDate() {
		return lastDealDate;
	}

	public boolean isActive() {
		return active;
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

		return value;
	}
}
