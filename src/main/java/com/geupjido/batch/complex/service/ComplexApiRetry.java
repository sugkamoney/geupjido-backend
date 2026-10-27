package com.geupjido.batch.complex.service;

import java.util.Objects;
import java.util.function.Predicate;
import java.util.function.Supplier;

import org.springframework.stereotype.Component;

/**
 * 호출자가 재시도 대상으로 판단한 오류만 제한된 지수 백오프로 다시 호출한다.
 */
@Component
public class ComplexApiRetry {

	private static final long[] BACKOFF_MILLIS = {2_000, 4_000, 8_000};

	private final Sleeper sleeper;

	public ComplexApiRetry() {
		this(Thread::sleep);
	}

	ComplexApiRetry(Sleeper sleeper) {
		this.sleeper = Objects.requireNonNull(sleeper);
	}

	public <T> T execute(
		Supplier<T> action,
		Predicate<RuntimeException> shouldRetry
	) {
		Objects.requireNonNull(action);
		Objects.requireNonNull(shouldRetry);

		for (int attempt = 0; ; attempt++) {
			try {
				return action.get();
			} catch (RuntimeException exception) {
				if (
					attempt >= BACKOFF_MILLIS.length
						|| !shouldRetry.test(exception)
				) {
					throw exception;
				}

				sleep(BACKOFF_MILLIS[attempt]);
			}
		}
	}

	private void sleep(long millis) {
		try {
			sleeper.sleep(millis);
		} catch (InterruptedException exception) {
			Thread.currentThread().interrupt();
			throw new IllegalStateException(
				"공동주택 API 재시도 대기 중 작업이 중단됐습니다.",
				exception
			);
		}
	}

	@FunctionalInterface
	interface Sleeper {
		void sleep(long millis) throws InterruptedException;
	}
}
