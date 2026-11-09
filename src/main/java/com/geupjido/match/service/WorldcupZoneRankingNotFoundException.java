package com.geupjido.match.service;

public class WorldcupZoneRankingNotFoundException extends RuntimeException {

	public WorldcupZoneRankingNotFoundException() {
		super("해당 권역을 찾을 수 없습니다.");
	}
}
