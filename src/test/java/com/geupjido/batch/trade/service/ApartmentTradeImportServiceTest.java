package com.geupjido.batch.trade.service;

import java.util.List;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InOrder;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import com.geupjido.batch.trade.exception.ApartmentTradeApiException;
import com.geupjido.batch.trade.model.ApartmentTradeData;
import com.geupjido.batch.trade.model.ApartmentTradeImportResult;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.Mockito.inOrder;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class ApartmentTradeImportServiceTest {

	@Mock
	private ApartmentTradeCollectionService collectionService;

	@Mock
	private ApartmentTradePersistenceService persistenceService;

	@Mock
	private ApartmentTradeBackfillPersistenceService backfillPersistenceService;

	@InjectMocks
	private ApartmentTradeImportService service;

	@Test
	void 월별_수집을_완료한_뒤_저장하고_결과를_반환한다() {
		ApartmentTradeData trade = mock(ApartmentTradeData.class);
		List<ApartmentTradeData> trades = List.of(trade);
		ApartmentTradeImportResult expected =
			new ApartmentTradeImportResult(1, 1, 0, 0);
		when(collectionService.collect("11170", "202609"))
			.thenReturn(trades);
		when(persistenceService.persist(trades)).thenReturn(expected);

		ApartmentTradeImportResult result =
			service.importMonth("11170", "202609");

		assertThat(result).isEqualTo(expected);
		InOrder inOrder = inOrder(
			collectionService,
			persistenceService
		);
		inOrder.verify(collectionService).collect("11170", "202609");
		inOrder.verify(persistenceService).persist(trades);
	}

	@Test
	void 수집이_실패하면_저장을_시작하지_않는다() {
		when(collectionService.collect("11170", "202609"))
			.thenThrow(new ApartmentTradeApiException("API 오류"));

		assertThatThrownBy(() -> service.importMonth("11170", "202609"))
			.isInstanceOf(ApartmentTradeApiException.class);

		verifyNoInteractions(persistenceService);
	}

	@Test
	void 빈_수집_결과는_0건_처리_결과로_반환한다() {
		ApartmentTradeImportResult empty =
			new ApartmentTradeImportResult(0, 0, 0, 0);
		when(collectionService.collect("11170", "202609"))
			.thenReturn(List.of());
		when(persistenceService.persist(List.of()))
			.thenReturn(empty);

		assertThat(service.importMonth("11170", "202609"))
			.isEqualTo(empty);
	}
}
