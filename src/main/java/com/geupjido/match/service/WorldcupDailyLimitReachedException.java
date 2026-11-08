package com.geupjido.match.service;

public class WorldcupDailyLimitReachedException extends RuntimeException {

	public WorldcupDailyLimitReachedException() {
		super("오늘 시작할 수 있는 월드컵 게임 6개를 모두 사용했습니다.");
	}
}
