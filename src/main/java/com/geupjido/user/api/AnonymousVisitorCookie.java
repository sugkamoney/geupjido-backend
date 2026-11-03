package com.geupjido.user.api;

import java.time.Duration;
import java.util.Optional;
import java.util.UUID;

import org.springframework.beans.factory.annotation.Value;
import org.springframework.http.ResponseCookie;
import org.springframework.stereotype.Component;

/** 계정 없이 같은 브라우저의 게임을 찾기 위한 서버 발급 식별 쿠키. */
@Component
public class AnonymousVisitorCookie {

	public static final String NAME = "gjd_uid";
	private static final Duration MAX_AGE = Duration.ofDays(365);

	private final boolean secure;

	public AnonymousVisitorCookie(@Value("${app.anonymous-cookie.secure:false}") boolean secure) {
		this.secure = secure;
	}

	public Visitor identifyForStart(String cookieValue) {
		Optional<UUID> existing = identify(cookieValue);
		if (existing.isPresent()) {
			return new Visitor(existing.get(), null);
		}
		UUID anonymousId = UUID.randomUUID();
		String setCookie = ResponseCookie.from(NAME, anonymousId.toString())
			.httpOnly(true)
			.secure(secure)
			.sameSite("Lax")
			.path("/")
			.maxAge(MAX_AGE)
			.build()
			.toString();
		return new Visitor(anonymousId, setCookie);
	}

	public Optional<UUID> identify(String cookieValue) {
		if (cookieValue == null) {
			return Optional.empty();
		}
		try {
			UUID anonymousId = UUID.fromString(cookieValue);
			return anonymousId.version() == 4 && anonymousId.toString().equals(cookieValue)
				? Optional.of(anonymousId) : Optional.empty();
		} catch (IllegalArgumentException exception) {
			return Optional.empty();
		}
	}

	public record Visitor(UUID anonymousId, String setCookie) {
	}
}
