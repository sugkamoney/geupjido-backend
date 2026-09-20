package com.geupjido.zone.domain;

import jakarta.persistence.Column;
import jakarta.persistence.Convert;
import jakarta.persistence.EmbeddedId;
import jakarta.persistence.Entity;
import jakarta.persistence.FetchType;
import jakarta.persistence.JoinColumn;
import jakarta.persistence.ManyToOne;
import jakarta.persistence.MapsId;
import jakarta.persistence.Table;

import java.util.Objects;

/**
 * 두 권역 사이의 인접 관계를 나타낸다.
 */
@Entity
@Table(name = "zone_adjacency")
public class ZoneAdjacency {

	@EmbeddedId
	private ZoneAdjacencyId id;

	@MapsId("zoneId")
	@ManyToOne(fetch = FetchType.LAZY, optional = false)
	@JoinColumn(name = "zone_id", nullable = false)
	private Zone zone;

	@MapsId("adjacentZoneId")
	@ManyToOne(fetch = FetchType.LAZY, optional = false)
	@JoinColumn(name = "adjacent_zone_id", nullable = false)
	private Zone adjacentZone;

	@Convert(converter = ZoneAdjacencySourceConverter.class)
	@Column(name = "source", length = 20, nullable = false)
	private ZoneAdjacencySource source;

	protected ZoneAdjacency() {
	}

	public ZoneAdjacency(
		Zone zone,
		Zone adjacentZone,
		ZoneAdjacencySource source
	) {
		this.zone = Objects.requireNonNull(zone);
		this.adjacentZone = Objects.requireNonNull(adjacentZone);
		this.source = Objects.requireNonNull(source);

		if (Objects.equals(zone.getId(), adjacentZone.getId())) {
			throw new IllegalArgumentException(
				"동일한 권역을 인접 권역으로 지정할 수 없습니다."
			);
		}

		this.id = new ZoneAdjacencyId(
			zone.getId(),
			adjacentZone.getId()
		);
	}

	public ZoneAdjacencyId getId() {
		return id;
	}

	public Zone getZone() {
		return zone;
	}

	public Zone getAdjacentZone() {
		return adjacentZone;
	}

	public ZoneAdjacencySource getSource() {
		return source;
	}
}
