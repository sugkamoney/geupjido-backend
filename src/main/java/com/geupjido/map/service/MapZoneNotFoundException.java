package com.geupjido.map.service;

public class MapZoneNotFoundException extends RuntimeException {
	public MapZoneNotFoundException(String zoneId) {
		super("권역을 찾을 수 없습니다: " + zoneId);
	}
}
