package com.geupjido.batch.complex.service;

import java.time.Duration;
import java.util.concurrent.TimeUnit;

import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

class ComplexApiPacerTest {

	@Test
	void 두_요청_시작_사이에_설정한_최소_간격을_둔다() {
		ComplexApiPacer pacer =
			new ComplexApiPacer(Duration.ofMillis(25));

		pacer.beforeRequest();
		long firstRequestAt = System.nanoTime();
		pacer.beforeRequest();

		assertThat(
			TimeUnit.NANOSECONDS.toMillis(
				System.nanoTime() - firstRequestAt
			)
		).isGreaterThanOrEqualTo(20);
	}

	@Test
	void 음수_호출_간격은_거부한다() {
		assertThatThrownBy(
			() -> new ComplexApiPacer(Duration.ofMillis(-1))
		).isInstanceOf(IllegalArgumentException.class);
	}
}
