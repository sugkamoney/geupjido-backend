package com.geupjido.batch.complex.service;

import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.atomic.AtomicInteger;

import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

class ComplexApiRetryTest {

	@Test
	void 재시도_대상_오류에_2초_4초_8초_간격을_적용한다() {
		List<Long> waits = new ArrayList<>();
		ComplexApiRetry retry = new ComplexApiRetry(waits::add);
		AtomicInteger calls = new AtomicInteger();

		String result = retry.execute(() -> {
			if (calls.incrementAndGet() < 4) {
				throw new IllegalStateException("일시적인 오류");
			}
			return "성공";
		}, exception -> true);

		assertThat(result).isEqualTo("성공");
		assertThat(calls.get()).isEqualTo(4);
		assertThat(waits).containsExactly(2_000L, 4_000L, 8_000L);
	}

	@Test
	void 재시도_대상이_아니면_즉시_실패한다() {
		List<Long> waits = new ArrayList<>();
		ComplexApiRetry retry = new ComplexApiRetry(waits::add);
		AtomicInteger calls = new AtomicInteger();

		assertThatThrownBy(() -> retry.execute(() -> {
			calls.incrementAndGet();
			throw new IllegalArgumentException("입력 오류");
		}, exception -> false))
			.isInstanceOf(IllegalArgumentException.class);

		assertThat(calls.get()).isEqualTo(1);
		assertThat(waits).isEmpty();
	}

	@Test
	void 마지막_재시도도_실패하면_오류를_그대로_전달한다() {
		List<Long> waits = new ArrayList<>();
		ComplexApiRetry retry = new ComplexApiRetry(waits::add);
		AtomicInteger calls = new AtomicInteger();

		assertThatThrownBy(() -> retry.execute(() -> {
			calls.incrementAndGet();
			throw new IllegalStateException("계속되는 오류");
		}, exception -> true))
			.isInstanceOf(IllegalStateException.class)
			.hasMessage("계속되는 오류");

		assertThat(calls.get()).isEqualTo(4);
		assertThat(waits).containsExactly(2_000L, 4_000L, 8_000L);
	}

	@Test
	void 대기_중_인터럽트되면_중단_상태를_복구한다() {
		ComplexApiRetry retry = new ComplexApiRetry(millis -> {
			throw new InterruptedException("중단");
		});
		try {
			assertThatThrownBy(() -> retry.execute(
				() -> { throw new IllegalStateException("일시적인 오류"); },
				exception -> true
			))
				.isInstanceOf(IllegalStateException.class)
				.hasMessageContaining("재시도 대기 중 작업이 중단");
			assertThat(Thread.currentThread().isInterrupted()).isTrue();
		} finally {
			Thread.interrupted();
		}
	}
}
