package com.geupjido.match.service;

public class WorldcupZoneSelectionNotFoundException extends RuntimeException {

	public WorldcupZoneSelectionNotFoundException() {
		super("존재하지 않는 권역입니다.");
	}
}
