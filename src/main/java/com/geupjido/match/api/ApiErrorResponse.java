package com.geupjido.match.api;

import io.swagger.v3.oas.annotations.media.Schema;

@Schema(description = "API 오류 응답")
public record ApiErrorResponse(String code, String message) {
}
