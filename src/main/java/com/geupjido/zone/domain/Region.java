package com.geupjido.zone.domain;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.Id;
import jakarta.persistence.Table;

/**
 * 서비스에서 사용하는 최상위 지역 분류를 나타낸다.
 */
@Entity
@Table(name = "region")
public class Region {

	@Id
	@Column(name = "code", length = 20, nullable = false)
	private String code;

	@Column(name = "name", length = 20, nullable = false)
	private String name;

	@Column(name = "is_active", nullable = false)
	private boolean active;

	@Column(name = "display_order", nullable = false)
	private int displayOrder;

	protected Region() {
	}

	public Region(String code, String name, boolean active, int displayOrder) {
		this.code = code;
		this.name = name;
		this.active = active;
		this.displayOrder = displayOrder;
	}

	public String getCode() {
		return code;
	}

	public String getName() {
		return name;
	}

	public boolean isActive() {
		return active;
	}

	public int getDisplayOrder() {
		return displayOrder;
	}
}
