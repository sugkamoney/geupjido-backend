package com.geupjido.zone.domain;

import jakarta.persistence.Column;
import jakarta.persistence.Embeddable;

import java.io.Serializable;
import java.util.Objects;

/**
 * 권역 인접 관계의 복합 기본키를 나타낸다.
 */
@Embeddable
public class ZoneAdjacencyId implements Serializable {

	@Column(name = "zone_id", length = 50, nullable = false)
	private String zoneId;

	@Column(name = "adjacent_zone_id", length = 50, nullable = false)
	private String adjacentZoneId;

	protected ZoneAdjacencyId() {
	}

	public ZoneAdjacencyId(String zoneId, String adjacentZoneId) {
		this.zoneId = zoneId;
		this.adjacentZoneId = adjacentZoneId;
	}

	public String getZoneId() {
		return zoneId;
	}

	public String getAdjacentZoneId() {
		return adjacentZoneId;
	}

	@Override
	public boolean equals(Object object) {
		if (this == object) {
			return true;
		}

		if (!(object instanceof ZoneAdjacencyId that)) {
			return false;
		}
		return Objects.equals(zoneId, that.zoneId)
			&& Objects.equals(adjacentZoneId, that.adjacentZoneId);
	}

	@Override
	public int hashCode() {
		return Objects.hash(zoneId, adjacentZoneId);
	}
}
