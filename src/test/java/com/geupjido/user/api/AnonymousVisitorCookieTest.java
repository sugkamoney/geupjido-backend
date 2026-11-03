package com.geupjido.user.api;

import java.util.UUID;

import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;

class AnonymousVisitorCookieTest {

	@Test
	void 첫_방문은_서버에서_익명_식별자를_발급하고_HttpOnly_쿠키를_설정한다() {
		AnonymousVisitorCookie cookie = new AnonymousVisitorCookie(true);

		AnonymousVisitorCookie.Visitor visitor = cookie.identifyForStart(null);

		assertThat(visitor.anonymousId().version()).isEqualTo(4);
		assertThat(visitor.setCookie())
			.contains("gjd_uid=" + visitor.anonymousId())
			.contains("Path=/")
			.contains("Max-Age=31536000")
			.contains("HttpOnly")
			.contains("Secure")
			.contains("SameSite=Lax");
	}

	@Test
	void 기존_쿠키는_다시_발급하지_않고_잘못된_쿠키는_새로_발급한다() {
		AnonymousVisitorCookie cookie = new AnonymousVisitorCookie(false);
		UUID anonymousId = UUID.randomUUID();

		assertThat(cookie.identifyForStart(anonymousId.toString()).anonymousId())
			.isEqualTo(anonymousId);
		assertThat(cookie.identifyForStart(anonymousId.toString()).setCookie()).isNull();
		assertThat(cookie.identify("invalid")).isEmpty();
		assertThat(cookie.identifyForStart("invalid").anonymousId()).isNotEqualTo(anonymousId);
	}
}
