package com.geupjido.zone.domain;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.FetchType;
import jakarta.persistence.Id;
import jakarta.persistence.JoinColumn;
import jakarta.persistence.ManyToOne;
import jakarta.persistence.Table;
import org.locationtech.jts.geom.MultiPolygon;
import org.locationtech.jts.geom.Point;

/**
 * 시·구 단위 행정지역과 공간 정보를 나타낸다.
 */
@Entity
@Table(name = "city")
public class City {

	@Id
	@Column(name = "id", length = 10, nullable = false)
	private String id;

	@ManyToOne(fetch = FetchType.LAZY, optional = false)
	@JoinColumn(name = "region_code", nullable = false)
	private Region region;

	@Column(name = "name", length = 50, nullable = false)
	private String name;

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

	protected City() {
	}

	public City(String id, Region region, String name, MultiPolygon polygon, Point center) {
		this.id = id;
		this.region = region;
		this.name = name;
		this.polygon = polygon;
		this.center = center;
	}

	public String getId() {
		return id;
	}

	public Region getRegion() {
		return region;
	}

	public String getName() {
		return name;
	}

	public MultiPolygon getPolygon() {
		return polygon;
	}

	public Point getCenter() {
		return center;
	}
}
