package com.geupjido.match.api;

import io.swagger.v3.oas.annotations.media.Schema;

@Schema(description = "현재 판에서 고른 단지")
public record WorldcupVoteRequest(String selectedComplexId) {
}
