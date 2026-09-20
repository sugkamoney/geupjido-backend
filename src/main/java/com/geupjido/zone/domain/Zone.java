package com.geupjido.zone.domain;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.FetchType;
import jakarta.persistence.Id;
import jakarta.persistence.JoinColumn;
import jakarta.persistence.ManyToOne;
import jakarta.persistence.Table;
import org.hibernate.annotations.JdbcTypeCode;
import org.hibernate.type.SqlTypes;
import org.locationtech.jts.geom.MultiPolygon;
import org.locationtech.jts.geom.Point;

import java.math.BigDecimal;
import java.time.LocalDateTime;

/**
 * 급지 산정 단위인 권역과 공간·평가 정보를 나타낸다.
 */
@Entity
@Table(name = "zone")
public class Zone {

	@Id
	@Column(name = "id", length = 50, nullable = false)
	private String id;

	@ManyToOne(fetch = FetchType.LAZY, optional = false)
	@JoinColumn(name = "city_id", nullable = false)
	private City city;

	@Column(name = "name", length = 50, nullable = false)
	private String name;

	@JdbcTypeCode(SqlTypes.ARRAY)
	@Column(
		name = "dong_codes",
		nullable = false,
		columnDefinition = "varchar(20)[]"
	)
	private String[] dongCodes;

	@Column(
		name = "polygon",
		nullable = false,
		columnDefinition = "geometry(MultiPolygon,4326)"
	)
	private MultiPolygon polygon;

	@Column(
		name = "center",
		nullable = false,
		columnDefinition = "geometry(Point,4326)"
	)
	private Point center;

	@Column(name = "tier", precision = 2, scale = 1)
	private BigDecimal tier;

	@Column(name = "score", precision = 8, scale = 2)
	private BigDecimal score;

	@Column(name = "rank")
	private Integer rank;

	@Column(name = "tier_updated_at")
	private LocalDateTime tierUpdatedAt;

	@Column(name = "price_pyeong")
	private Integer pricePyeong;

	@Column(name = "price_updated_at")
	private LocalDateTime priceUpdatedAt;

	protected Zone() {
	}

	public Zone(
		String id,
		City city,
		String name,
		String[] dongCodes,
		MultiPolygon polygon,
		Point center
	) {
		this.id = id;
		this.city = city;
		this.name = name;
		this.dongCodes = dongCodes.clone();
		this.polygon = polygon;
		this.center = center;
	}

	public String getId() {
		return id;
	}

	public City getCity() {
		return city;
	}

	public String getName() {
		return name;
	}

	public String[] getDongCodes() {
		return dongCodes.clone();
	}

	public MultiPolygon getPolygon() {
		return polygon;
	}

	public Point getCenter() {
		return center;
	}

	public BigDecimal getTier() {
		return tier;
	}

	public BigDecimal getScore() {
		return score;
	}

	public Integer getRank() {
		return rank;
	}

	public LocalDateTime getTierUpdatedAt() {
		return tierUpdatedAt;
	}

	public Integer getPricePyeong() {
		return pricePyeong;
	}

	public LocalDateTime getPriceUpdatedAt() {
		return priceUpdatedAt;
	}
}
