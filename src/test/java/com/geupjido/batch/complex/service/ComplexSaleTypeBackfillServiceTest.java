package com.geupjido.batch.complex.service;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.List;
import java.util.Optional;

import org.junit.jupiter.api.Test;
import org.springframework.data.domain.PageRequest;
import org.springframework.transaction.TransactionStatus;
import org.springframework.transaction.support.TransactionCallback;
import org.springframework.transaction.support.TransactionTemplate;

import com.geupjido.batch.complex.client.ComplexBasicInfoApiClient;
import com.geupjido.batch.complex.dto.ComplexBasicInfoApiItem;
import com.geupjido.batch.complex.exception.ComplexBasicInfoApiException;
import com.geupjido.complex.domain.Complex;
import com.geupjido.complex.repository.ComplexRepository;
import com.geupjido.trade.repository.ApartmentTradeRepository;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.doAnswer;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

class ComplexSaleTypeBackfillServiceTest {

	@Test
	void 기존_단지의_분양형태만_채우고_응답이_다른_ID는_보류한다() {
		ComplexRepository repository = mock(ComplexRepository.class);
		ApartmentTradeRepository tradeRepository = mock(ApartmentTradeRepository.class);
		ComplexBasicInfoApiClient apiClient = mock(ComplexBasicInfoApiClient.class);
		TransactionTemplate transactionTemplate = mock(TransactionTemplate.class);
		Complex existing = new Complex("A2", "단지", "서울 중구 신당동 844",
			null, "1114016200", 100, null, LocalDate.of(2002, 5, 25));
		existing.updateRepresentativeTrade(120000,
			new BigDecimal("59.940000"), LocalDate.of(2026, 9, 1));
		when(repository.findIdsMissingSaleTypeAfterId("", PageRequest.of(0, 2)))
			.thenReturn(List.of("A1", "A2"));
		when(apiClient.fetchByComplexCode("A1"))
			.thenReturn(item("OTHER", "임대"));
		when(apiClient.fetchByComplexCode("A2"))
			.thenReturn(item("A2", "분양"));
		when(repository.findById("A2")).thenReturn(Optional.of(existing));
		doAnswer(invocation -> invocation.getArgument(0,
			TransactionCallback.class).doInTransaction(mock(TransactionStatus.class)))
			.when(transactionTemplate).execute(any());

		var result = new ComplexSaleTypeBackfillService(repository, tradeRepository, apiClient,
			new ComplexApiRetry(), transactionTemplate).backfill("", 2);

		assertThat(result.updated()).isEqualTo(1);
		assertThat(result.failedIds()).containsExactly("A1");
		assertThat(existing.getSaleType()).isEqualTo("분양");
		assertThat(existing.getPrice()).isEqualTo(120000);
		assertThat(existing.getRepresentativeArea())
			.isEqualByComparingTo("59.940000");
	}

	@Test
	void 가격이_빈_단지만_선택해_분양형태를_보충한다() {
		ComplexRepository repository = mock(ComplexRepository.class);
		ApartmentTradeRepository tradeRepository = mock(ApartmentTradeRepository.class);
		ComplexBasicInfoApiClient apiClient = mock(ComplexBasicInfoApiClient.class);
		TransactionTemplate transactionTemplate = mock(TransactionTemplate.class);
		Complex existing = new Complex("A1", "단지", "서울 중구 신당동 844",
			null, "1114016200", 100, null, LocalDate.of(2002, 5, 25));
		when(repository.findIdsMissingSaleTypeAndPriceAfterId("",
			PageRequest.of(0, 1))).thenReturn(List.of("A1"));
		when(apiClient.fetchByComplexCode("A1"))
			.thenReturn(item("A1", "분양"));
		when(repository.findById("A1")).thenReturn(Optional.of(existing));
		doAnswer(invocation -> invocation.getArgument(0,
			TransactionCallback.class).doInTransaction(mock(TransactionStatus.class)))
			.when(transactionTemplate).execute(any());

		var result = new ComplexSaleTypeBackfillService(repository,
			tradeRepository, apiClient, new ComplexApiRetry(),
			transactionTemplate).backfill("", 1, true);

		assertThat(result.updated()).isEqualTo(1);
		assertThat(existing.getSaleType()).isEqualTo("분양");
		verify(repository, never()).findIdsMissingSaleTypeAfterId("",
			PageRequest.of(0, 1));
	}

	@Test
	void API_04가_연속_발생하면_남은_호출을_멈춘다() {
		ComplexRepository repository = mock(ComplexRepository.class);
		ApartmentTradeRepository tradeRepository = mock(ApartmentTradeRepository.class);
		ComplexBasicInfoApiClient apiClient = mock(ComplexBasicInfoApiClient.class);
		TransactionTemplate transactionTemplate = mock(TransactionTemplate.class);
		when(repository.findIdsMissingSaleTypeAfterId("", PageRequest.of(0, 4)))
			.thenReturn(List.of("A1", "A2", "A3", "A4"));
		for (String id : List.of("A1", "A2", "A3")) {
			when(apiClient.fetchByComplexCode(id)).thenThrow(
				new ComplexBasicInfoApiException("일시 오류", "04")
			);
		}

		var result = new ComplexSaleTypeBackfillService(repository, tradeRepository, apiClient,
			new ComplexApiRetry(millis -> {}), transactionTemplate)
			.backfill("", 4);

		assertThat(result.apiUnavailable()).isTrue();
		assertThat(result.failedIds()).containsExactly("A1", "A2", "A3");
		verify(apiClient, never()).fetchByComplexCode("A4");
	}

	@Test
	void 임대_전용으로_확인되면_거래_연결을_풀고_단지를_삭제한다() {
		ComplexRepository repository = mock(ComplexRepository.class);
		ApartmentTradeRepository tradeRepository = mock(ApartmentTradeRepository.class);
		ComplexBasicInfoApiClient apiClient = mock(ComplexBasicInfoApiClient.class);
		TransactionTemplate transactionTemplate = mock(TransactionTemplate.class);
		Complex existing = new Complex("A1", "임대단지", "서울 중구 신당동 844",
			null, "1114016200", 100, null, LocalDate.of(2002, 5, 25));
		when(repository.findIdsMissingSaleTypeAfterId("", PageRequest.of(0, 1)))
			.thenReturn(List.of("A1"));
		when(apiClient.fetchByComplexCode("A1")).thenReturn(item("A1", "임대"));
		when(repository.findById("A1")).thenReturn(Optional.of(existing));
		doAnswer(invocation -> invocation.getArgument(0,
			TransactionCallback.class).doInTransaction(mock(TransactionStatus.class)))
			.when(transactionTemplate).execute(any());

		var result = new ComplexSaleTypeBackfillService(repository, tradeRepository,
			apiClient, new ComplexApiRetry(), transactionTemplate).backfill("", 1);

		assertThat(result.updated()).isZero();
		assertThat(result.excluded()).isEqualTo(1);
		var order = org.mockito.Mockito.inOrder(tradeRepository, repository);
		order.verify(tradeRepository).detachLinksForComplex("A1");
		order.verify(repository).delete(existing);
	}

	@Test
	void 사택_관사로_확인되면_거래_연결을_풀고_단지를_삭제한다() {
		ComplexRepository repository = mock(ComplexRepository.class);
		ApartmentTradeRepository tradeRepository = mock(ApartmentTradeRepository.class);
		ComplexBasicInfoApiClient apiClient = mock(ComplexBasicInfoApiClient.class);
		TransactionTemplate transactionTemplate = mock(TransactionTemplate.class);
		Complex existing = new Complex("A1", "사택단지", "서울 중구 신당동 844",
			null, "1114016200", 100, null, LocalDate.of(2002, 5, 25));
		when(repository.findIdsMissingSaleTypeAfterId("", PageRequest.of(0, 1)))
			.thenReturn(List.of("A1"));
		when(apiClient.fetchByComplexCode("A1"))
			.thenReturn(item("A1", "사택 및 관사 등"));
		when(repository.findById("A1")).thenReturn(Optional.of(existing));
		doAnswer(invocation -> invocation.getArgument(0,
			TransactionCallback.class).doInTransaction(mock(TransactionStatus.class)))
			.when(transactionTemplate).execute(any());

		var result = new ComplexSaleTypeBackfillService(repository, tradeRepository,
			apiClient, new ComplexApiRetry(), transactionTemplate).backfill("", 1);

		assertThat(result.excluded()).isEqualTo(1);
		var order = org.mockito.Mockito.inOrder(tradeRepository, repository);
		order.verify(tradeRepository).detachLinksForComplex("A1");
		order.verify(repository).delete(existing);
	}

	private static ComplexBasicInfoApiItem item(String code, String saleType) {
		return new ComplexBasicInfoApiItem(code, "단지", "서울 중구 신당동 844",
			null, "1", new BigDecimal("100"), "20020525",
			"1114016200", saleType);
	}
}
