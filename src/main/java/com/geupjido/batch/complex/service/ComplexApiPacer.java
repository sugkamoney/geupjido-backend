package com.geupjido.batch.complex.service;

import java.time.Duration;
import java.util.concurrent.TimeUnit;

import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Component;

/**
 * 단지 목록과 기본정보 API의 요청 시작 간격을 한 프로세스 안에서 제한한다.
 */
@Component
public class ComplexApiPacer {

	private final long intervalNanos;
	private long lastRequestNanos;
	private boolean requested;

	public ComplexApiPacer(
		@Value("${app.complex-import.request-interval:0ms}") Duration interval
	) {
		if (interval.isNegative()) {
			throw new IllegalArgumentException(
				"공동주택 API 호출 간격은 음수일 수 없습니다."
			);
		}
		this.intervalNanos = interval.toNanos();
	}

	public synchronized void beforeRequest() {
		if (intervalNanos == 0) {
			return;
		}

		if (requested) {
			long elapsed = System.nanoTime() - lastRequestNanos;
			long remaining = intervalNanos - elapsed;
			if (remaining > 0) {
				try {
					TimeUnit.NANOSECONDS.sleep(remaining);
				} catch (InterruptedException exception) {
					Thread.currentThread().interrupt();
					throw new IllegalStateException(
						"공동주택 API 호출 대기 중 작업이 중단됐습니다.",
						exception
					);
				}
			}
		}

		lastRequestNanos = System.nanoTime();
		requested = true;
	}
}
