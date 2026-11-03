package com.geupjido.match.service;

/** 현재 활성 후보로 완전한 10판을 구성할 수 없는 경우. */
public class MatchUnavailableException extends RuntimeException {
	public MatchUnavailableException(String message) {
		super(message);
	}
}
