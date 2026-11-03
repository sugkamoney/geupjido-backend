package com.geupjido.match.service;

public class WorldcupGameNotFoundException extends RuntimeException {

	public WorldcupGameNotFoundException() {
		super("진행 중인 월드컵 게임이 없습니다.");
	}
}
