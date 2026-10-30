package com.geupjido.batch.trade.service;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.List;

import org.junit.jupiter.api.Test;

import com.geupjido.batch.trade.model.ApartmentTradeScope;
import com.geupjido.batch.trade.model.ApartmentTradeScope.Status;
import com.geupjido.complex.domain.Complex;
import com.geupjido.complex.repository.ComplexRepository;
import com.geupjido.trade.domain.ApartmentTrade;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;

class ApartmentTradeCandidateFinderTest {

	private final ComplexRepository repository = mock(ComplexRepository.class);
	private final ApartmentTradeCandidateFinder finder =
		new ApartmentTradeCandidateFinder(repository);

	@Test
	void 같은_법정동의_정확한_지번만_후보로_남긴다() {
		ApartmentTrade trade = trade("한강로2가", "417");
		ApartmentTradeScope scope = new ApartmentTradeScope(
			Status.IN_SCOPE, "1117012500", "yongsan-hangangro"
		);
		Complex exact = complex("A1", "한강로2가 417");
		Complex subLot = complex("A2", "한강로2가 417-1");
		Complex numberSuffix = complex("A3", "한강로2가 4170");
		Complex otherDong = complex("A4", "한강로3가 417");
		when(repository.findByLegalDongCode("1117012500"))
			.thenReturn(List.of(exact, subLot, numberSuffix, otherDong));

		assertThat(finder.findCandidates(trade, scope))
			.containsExactly(exact);
		verify(repository).findByLegalDongCode("1117012500");
	}

	@Test
	void 같은_지번의_임대_전용은_제외하고_분양만_남긴다() {
		ApartmentTrade trade = trade("한강로2가", "417");
		ApartmentTradeScope scope = new ApartmentTradeScope(
			Status.IN_SCOPE, "1117012500", "yongsan-hangangro"
		);
		Complex rental = complex("R", "한강로2가 417");
		rental.updateSaleType("임대");
		Complex sale = complex("S", "한강로2가 417");
		sale.updateSaleType("분양");
		Complex mixed = complex("M", "한강로2가 417");
		mixed.updateSaleType("분양/임대");
		when(repository.findByLegalDongCode("1117012500"))
			.thenReturn(List.of(rental, sale, mixed));

		assertThat(finder.findCandidates(trade, scope))
			.containsExactly(sale, mixed);
	}

	@Test
	void 분양형태가_확인된_단지만_지번의_영번_표기_차이를_허용한다() {
		ApartmentTrade trade = trade("한강로2가", "417");
		ApartmentTradeScope scope = new ApartmentTradeScope(
			Status.IN_SCOPE, "1117012500", "yongsan-hangangro"
		);
		Complex unverified = complex("U", "한강로2가 417-0000");
		Complex sale = complex("S", "한강로2가 0417-0000");
		sale.updateSaleType("분양");
		Complex otherLot = complex("O", "한강로2가 417-1");
		otherLot.updateSaleType("분양");
		when(repository.findByLegalDongCode("1117012500"))
			.thenReturn(List.of(unverified, sale, otherLot));

		assertThat(finder.findCandidates(trade, scope))
			.containsExactly(sale);
	}

	@Test
	void 대상밖_거래는_후보를_조회하지_않는다() {
		ApartmentTradeScope scope = new ApartmentTradeScope(
			Status.OUT_OF_SCOPE, "1117010700", null
		);

		assertThatThrownBy(() -> finder.findCandidates(
			trade("동자동", "1"), scope
		)).isInstanceOf(IllegalArgumentException.class);
		verifyNoInteractions(repository);
	}

	@Test
	void 지번이_없으면_빈_후보를_반환한다() {
		ApartmentTradeScope scope = new ApartmentTradeScope(
			Status.IN_SCOPE, "1117012500", "yongsan-hangangro"
		);

		assertThat(finder.findCandidates(
			trade("한강로2가", null), scope
		)).isEmpty();
		verifyNoInteractions(repository);
	}

	private static ApartmentTrade trade(String dongName, String lotNumber) {
		return new ApartmentTrade(
			LocalDate.of(2026, 9, 1), 100_000L,
			new BigDecimal("84.0"), (short) 1, (short) 2007,
			"11170", dongName, "테스트아파트", lotNumber,
			null, null, false, null
		);
	}

	private static Complex complex(String id, String dongAndLot) {
		return new Complex(
			id, "테스트아파트", "서울특별시 용산구 " + dongAndLot,
			null, "1117012500", 100, null, null
		);
	}
}
