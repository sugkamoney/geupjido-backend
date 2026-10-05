package com.geupjido.batch.trade.service;

import java.util.List;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InOrder;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import com.geupjido.batch.trade.client.ApartmentTradeApiClient;
import com.geupjido.batch.trade.dto.ApartmentTradeApiItem;
import com.geupjido.batch.trade.exception.ApartmentTradeApiException;
import com.geupjido.batch.trade.exception.ApartmentTradeMappingException;
import com.geupjido.batch.trade.mapper.ApartmentTradeMapper;
import com.geupjido.batch.trade.model.ApartmentTradeData;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.Mockito.inOrder;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class ApartmentTradeCollectionServiceTest {

	@Mock
	private ApartmentTradeApiClient apiClient;

	@Mock
	private ApartmentTradeMapper mapper;

	@InjectMocks
	private ApartmentTradeCollectionService service;

	@Test
	void 월별_실거래가를_조회하고_순서대로_변환한다() {
		ApartmentTradeApiItem firstItem =
			mock(ApartmentTradeApiItem.class);
		ApartmentTradeApiItem secondItem =
			mock(ApartmentTradeApiItem.class);

		ApartmentTradeData firstTrade =
			mock(ApartmentTradeData.class);
		ApartmentTradeData secondTrade =
			mock(ApartmentTradeData.class);

		when(apiClient.fetchAll("11170", "202609"))
			.thenReturn(List.of(firstItem, secondItem));
		when(mapper.map(firstItem)).thenReturn(firstTrade);
		when(mapper.map(secondItem)).thenReturn(secondTrade);

		List<ApartmentTradeData> result =
			service.collect("11170", "202609");

		assertThat(result)
			.containsExactly(firstTrade, secondTrade);

		InOrder inOrder = inOrder(apiClient, mapper);
		inOrder.verify(apiClient)
			.fetchAll("11170", "202609");
		inOrder.verify(mapper).map(firstItem);
		inOrder.verify(mapper).map(secondItem);
	}

	@Test
	void 조회된_실거래가가_없으면_빈_목록을_반환한다() {
		when(apiClient.fetchAll("11170", "202609"))
			.thenReturn(List.of());

		List<ApartmentTradeData> result =
			service.collect("11170", "202609");

		assertThat(result).isEmpty();

		verify(apiClient).fetchAll("11170", "202609");
		verifyNoInteractions(mapper);
	}

	@Test
	void 실거래가_API_호출이_실패하면_예외를_그대로_전달한다() {
		when(apiClient.fetchAll("11170", "202609"))
			.thenThrow(
				new ApartmentTradeApiException(
					"아파트 매매 실거래가 API 호출에 실패했습니다."
				)
			);

		assertThatThrownBy(
			() -> service.collect("11170", "202609")
		)
			.isInstanceOf(ApartmentTradeApiException.class)
			.hasMessageContaining(
				"아파트 매매 실거래가 API 호출에 실패했습니다"
			);

		verifyNoInteractions(mapper);
	}

	@Test
	void 실거래가_변환이_실패하면_예외를_그대로_전달한다() {
		ApartmentTradeApiItem item =
			mock(ApartmentTradeApiItem.class);

		when(apiClient.fetchAll("11170", "202609"))
			.thenReturn(List.of(item));
		when(mapper.map(item))
			.thenThrow(
				new ApartmentTradeMappingException(
					"거래금액을 숫자로 변환할 수 없습니다."
				)
			);

		assertThatThrownBy(
			() -> service.collect("11170", "202609")
		)
			.isInstanceOf(ApartmentTradeMappingException.class)
			.hasMessageContaining(
				"거래금액을 숫자로 변환할 수 없습니다"
			);
	}
}
