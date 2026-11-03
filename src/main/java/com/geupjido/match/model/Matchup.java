package com.geupjido.match.model;

/** 두 단지 중 하나를 고르는 한 판. */
public record Matchup(MatchType type, MatchCard left, MatchCard right) {
}
