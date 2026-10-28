package com.geupjido.batch.trade.service;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.List;

import org.junit.jupiter.api.Test;

import com.geupjido.batch.trade.model.ApartmentTradeCandidateAssessment;
import com.geupjido.batch.trade.model.ApartmentTradeCandidateAssessment.Status;
import com.geupjido.batch.trade.model.ApartmentTradeScope;
import com.geupjido.complex.domain.Complex;
import com.geupjido.trade.domain.ApartmentTrade;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

class ApartmentTradeCandidateAssessmentServiceTest {

	private final ApartmentTradeCandidateFinder finder =
		mock(ApartmentTradeCandidateFinder.class);
	private final ApartmentTradeCandidateAssessmentService service =
		new ApartmentTradeCandidateAssessmentService(finder);
	private final ApartmentTradeScope scope = new ApartmentTradeScope(
		ApartmentTradeScope.Status.IN_SCOPE,
		"1117012900", "yongsan-ichon"
	);

	@Test
	void 후보가_없거나_여러_개면_연결_대상을_고르지_않는다() {
		ApartmentTrade trade = trade("한가람", (short) 1998);
		when(finder.findCandidates(trade, scope)).thenReturn(List.of());
		assertThat(service.assess(trade, scope))
			.isEqualTo(new ApartmentTradeCandidateAssessment(
				Status.NO_CANDIDATE, List.of()
			));

		when(finder.findCandidates(trade, scope)).thenReturn(List.of(
			complex("B", "한가람", LocalDate.of(1998, 1, 1)),
			complex("A", "한가람", LocalDate.of(1998, 1, 1))
		));
		assertThat(service.assess(trade, scope))
			.isEqualTo(new ApartmentTradeCandidateAssessment(
				Status.MULTIPLE_CANDIDATES, List.of("A", "B")
			));
	}

	@Test
	void 연도_근거가_없거나_다르면_이름이_같아도_확정하지_않는다() {
		ApartmentTrade trade = trade("한가람", (short) 1998);
		when(finder.findCandidates(trade, scope)).thenReturn(List.of(
			complex("A", "한가람", null)
		));
		assertThat(service.assess(trade, scope).status())
			.isEqualTo(Status.YEAR_UNVERIFIED);

		when(finder.findCandidates(trade, scope)).thenReturn(List.of(
			complex("A", "한가람", LocalDate.of(1997, 1, 1))
		));
		assertThat(service.assess(trade, scope).status())
			.isEqualTo(Status.YEAR_MISMATCH);
	}

	@Test
	void 같은_연도에서_이름의_공백만_다르면_일치하고_다른_이름은_검토한다() {
		ApartmentTrade trade = trade("한가람", (short) 1998);
		when(finder.findCandidates(trade, scope)).thenReturn(List.of(
			complex("A", "한 가 람", LocalDate.of(1998, 9, 18))
		));
		assertThat(service.assess(trade, scope))
			.isEqualTo(new ApartmentTradeCandidateAssessment(
				Status.NAME_EXACT, List.of("A")
			));

		when(finder.findCandidates(trade, scope)).thenReturn(List.of(
			complex("A", "이촌한가람아파트", LocalDate.of(1998, 9, 18))
		));
		assertThat(service.assess(trade, scope))
			.isEqualTo(new ApartmentTradeCandidateAssessment(
				Status.NAME_REVIEW, List.of("A")
			));
	}

	private static ApartmentTrade trade(String name, Short buildYear) {
		return new ApartmentTrade(
			LocalDate.of(2026, 9, 1), 100_000L,
			new BigDecimal("84.0"), (short) 1, buildYear,
			"11170", "이촌동", name, "404", null, null,
			false, null
		);
	}

	private static Complex complex(
		String id,
		String name,
		LocalDate approvalDate
	) {
		return new Complex(
			id, name, "서울특별시 용산구 이촌동 404 " + name,
			null, "1117012900", 100, null, approvalDate
		);
	}
}
