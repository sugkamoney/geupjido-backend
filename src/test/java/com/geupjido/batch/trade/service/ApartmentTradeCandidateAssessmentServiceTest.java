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
	void 연도_근거가_없거나_달라도_유일한_지번과_이름으로_확정한다() {
		ApartmentTrade trade = trade("한가람", (short) 1998);
		when(finder.findCandidates(trade, scope)).thenReturn(List.of(
			complex("A", "한가람", null)
		));
		assertThat(service.assess(trade, scope).status())
			.isEqualTo(Status.NAME_EXACT);

		when(finder.findCandidates(trade, scope)).thenReturn(List.of(
			complex("A", "한가람", LocalDate.of(1997, 1, 1))
		));
		assertThat(service.assess(trade, scope).status())
			.isEqualTo(Status.NAME_EXACT);

		assertThat(service.assess(trade("한가람", null),
			List.of(complex("A", "한가람", null))).status())
			.isEqualTo(Status.NAME_EXACT);
	}

	@Test
	void 지번_후보가_여럿이면_분양형태가_확인된_유일한_동명이인만_선택한다() {
		ApartmentTrade trade = trade("한가람", (short) 1998);
		Complex unrelated = complex("B", "다른단지",
			LocalDate.of(1998, 1, 1));
		Complex named = complex("A", "한가람",
			LocalDate.of(1998, 1, 1));
		List<Complex> candidates = List.of(unrelated, named);

		assertThat(service.assess(trade, candidates).status())
			.isEqualTo(Status.MULTIPLE_CANDIDATES);

		named.updateSaleType("분양");
		assertThat(service.assess(trade, candidates))
			.isEqualTo(new ApartmentTradeCandidateAssessment(
				Status.NAME_EXACT, List.of("A")
			));

		List<Complex> twoNamed = List.of(named,
			complex("C", "한가람", LocalDate.of(1998, 1, 1)));
		assertThat(service.assess(trade, twoNamed).status())
			.isEqualTo(Status.MULTIPLE_CANDIDATES);
	}

	@Test
	void 같은_연도에서_공백과_끝의_아파트_표기만_다르면_일치한다() {
		ApartmentTrade trade = trade("한가람", (short) 1998);
		when(finder.findCandidates(trade, scope)).thenReturn(List.of(
			complex("A", "한 가 람", LocalDate.of(1998, 9, 18))
		));
		assertThat(service.assess(trade, scope))
			.isEqualTo(new ApartmentTradeCandidateAssessment(
				Status.NAME_EXACT, List.of("A")
			));

		when(finder.findCandidates(trade, scope)).thenReturn(List.of(
			complex("A", "한가람아파트", LocalDate.of(1998, 9, 18))
		));
		assertThat(service.assess(trade, scope).status())
			.isEqualTo(Status.NAME_EXACT);

		when(finder.findCandidates(trade, scope)).thenReturn(List.of(
			complex("A", "이촌한가람아파트", LocalDate.of(1998, 9, 18))
		));
		assertThat(service.assess(trade, scope))
			.isEqualTo(new ApartmentTradeCandidateAssessment(
				Status.NAME_REVIEW, List.of("A")
			));
	}

	@Test
	void 검증된_이름_별칭만_자동_연결_대상으로_판정한다() {
		ApartmentTrade trade = new ApartmentTrade(
			LocalDate.of(2026, 9, 1), 100_000L,
			new BigDecimal("59.8"), (short) 1, (short) 2002,
			"11140", "신당동", "남산타운", "844", null,
			"중개거래", false, null
		);
		Complex sale = new Complex("A10045302", "신당남산타운(분양)",
			"서울특별시 중구 신당동 844", null, "1114016200",
			100, null, LocalDate.of(2002, 5, 25));
		assertThat(service.assess(trade, List.of(sale)).status())
			.isEqualTo(Status.NAME_APPROVED_ALIAS);

		Complex unrelated = new Complex("OTHER", "다른 단지",
			"서울특별시 중구 신당동 844", null, "1114016200",
			100, null, LocalDate.of(2002, 5, 25));
		assertThat(service.assess(trade, List.of(unrelated)).status())
			.isEqualTo(Status.NAME_REVIEW);
	}

	@Test
	void 단독_지번에서_확인한_표기_차이만_별칭으로_인정한다() {
		ApartmentTrade trade = new ApartmentTrade(
			LocalDate.of(2026, 9, 1), 100_000L,
			new BigDecimal("59.8"), (short) 1, (short) 2001,
			"11170", "한남동", "리버탑", "802", null,
			"중개거래", false, null
		);
		Complex verified = new Complex("A10023379", "한남리버탑아파트",
			"서울특별시 용산구 한남동 802", null, "1117013100",
			100, null, LocalDate.of(2001, 1, 1));
		assertThat(service.assess(trade, List.of(verified)).status())
			.isEqualTo(Status.NAME_APPROVED_ALIAS);

		ApartmentTrade differentLot = new ApartmentTrade(
			LocalDate.of(2026, 9, 1), 100_000L,
			new BigDecimal("59.8"), (short) 1, (short) 2001,
			"11170", "한남동", "리버탑", "803", null,
			"중개거래", false, null
		);
		assertThat(service.assess(differentLot, List.of(verified)).status())
			.isEqualTo(Status.NAME_REVIEW);
	}

	@Test
	void 공유_지번의_동일_도로명에서도_검증된_별칭으로_단지를_구분한다() {
		ApartmentTrade trade = new ApartmentTrade(
			LocalDate.of(2026, 9, 1), 100_000L,
			new BigDecimal("84.0"), (short) 1, (short) 1985,
			"11215", "광장동", "극동1", "218-1", null,
			"중개거래", false, null
		);
		Complex first = new Complex("A14380409", "광장극동1차",
			"서울특별시 광진구 광장동 218-1", null, "1121510400",
			100, null, LocalDate.of(1985, 9, 14));
		first.updateSaleType("분양");
		Complex second = new Complex("A14380414", "극동2차아파트",
			"서울특별시 광진구 광장동 218-1", null, "1121510400",
			100, null, LocalDate.of(1989, 3, 24));
		second.updateSaleType("분양");

		assertThat(service.assess(trade, List.of(first, second)))
			.isEqualTo(new ApartmentTradeCandidateAssessment(
				Status.NAME_APPROVED_ALIAS, List.of("A14380409")
			));
	}

	@Test
	void 공유_지번의_시공사명이_서로_다르면_검증된_단지만_선택한다() {
		ApartmentTrade trade = new ApartmentTrade(
			LocalDate.of(2026, 9, 1), 100_000L,
			new BigDecimal("59.46"), (short) 1, (short) 1992,
			"41135", "이매동", "아름마을(한성)", "132", null,
			"중개거래", false, null
		);
		Complex geonyeong = new Complex("A46373111", "이매아름마을건영",
			"경기도 성남시 분당구 이매동 132", null, "4113510600",
			100, null, null);
		Complex taeyeong = new Complex("A46373506", "이매아름마을태영",
			"경기도 성남시 분당구 이매동 132", null, "4113510600",
			100, null, null);
		Complex hanseong = new Complex("A46380601", "이매아름마을한성",
			"경기도 성남시 분당구 이매동 132", null, "4113510600",
			100, null, null);
		List<Complex> candidates = List.of(geonyeong, taeyeong, hanseong);
		assertThat(service.assess(trade, candidates).status())
			.isEqualTo(Status.MULTIPLE_CANDIDATES);
		candidates.forEach(candidate -> candidate.updateSaleType("분양"));
		assertThat(service.assess(trade, candidates))
			.isEqualTo(new ApartmentTradeCandidateAssessment(
				Status.NAME_APPROVED_ALIAS, List.of("A46380601")
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
