package com.geupjido.batch.complex.service;

import java.util.List;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InOrder;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import com.geupjido.batch.complex.client.ComplexListApiClient;
import com.geupjido.batch.complex.dto.ComplexListApiItem;
import com.geupjido.batch.complex.exception.ComplexListApiException;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.Mockito.inOrder;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.verifyNoMoreInteractions;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class ComplexListCollectionServiceTest {

	@Mock
	private ComplexListApiClient apiClient;

	@InjectMocks
	private ComplexListCollectionService service;

	@Test
	void 여러_법정동의_단지_목록을_입력_순서대로_수집한다() {
		ComplexListApiItem hannamComplex =
			new ComplexListApiItem(
				"A10000001",
				"한남 테스트 아파트",
				"1117013100"
			);

		ComplexListApiItem seongsuComplex =
			new ComplexListApiItem(
				"A10000002",
				"성수 테스트 아파트",
				"1120011400"
			);

		when(
			apiClient.fetchAllByLegalDongCode("1117013100")
		).thenReturn(List.of(hannamComplex));

		when(
			apiClient.fetchAllByLegalDongCode("1120011400")
		).thenReturn(List.of(seongsuComplex));

		List<ComplexListApiItem> result =
			service.collectByLegalDongCodes(
				List.of(
					"1117013100",
					"1120011400"
				)
			);

		assertThat(result)
			.containsExactly(
				hannamComplex,
				seongsuComplex
			);

		InOrder inOrder = inOrder(apiClient);
		inOrder.verify(apiClient)
			.fetchAllByLegalDongCode("1117013100");
		inOrder.verify(apiClient)
			.fetchAllByLegalDongCode("1120011400");
	}

	@Test
	void 법정동_코드_목록이_비어_있으면_수집하지_않는다() {
		assertThatThrownBy(
			() -> service.collectByLegalDongCodes(List.of())
		)
			.isInstanceOf(IllegalArgumentException.class)
			.hasMessageContaining(
				"법정동 코드 목록은 비어 있을 수 없습니다"
			);

		verifyNoInteractions(apiClient);
	}

	@Test
	void 잘못된_법정동_코드가_있으면_수집을_시작하지_않는다() {
		assertThatThrownBy(
			() -> service.collectByLegalDongCodes(
				List.of(
					"1117013100",
					"잘못된코드"
				)
			)
		)
			.isInstanceOf(IllegalArgumentException.class)
			.hasMessageContaining(
				"법정동 코드는 숫자 10자리여야 합니다"
			);

		verifyNoInteractions(apiClient);
	}

	@Test
	void 중복된_법정동_코드가_있으면_수집하지_않는다() {
		assertThatThrownBy(
			() -> service.collectByLegalDongCodes(
				List.of(
					"1117013100",
					"1117013100"
				)
			)
		)
			.isInstanceOf(IllegalArgumentException.class)
			.hasMessageContaining(
				"중복된 법정동 코드가 있습니다"
			);

		verifyNoInteractions(apiClient);
	}

	@Test
	void 중간_API_호출이_실패하면_이후_법정동은_수집하지_않는다() {
		ComplexListApiItem hannamComplex =
			new ComplexListApiItem(
				"A10000001",
				"한남 테스트 아파트",
				"1117013100"
			);

		when(
			apiClient.fetchAllByLegalDongCode("1117013100")
		).thenReturn(List.of(hannamComplex));

		when(
			apiClient.fetchAllByLegalDongCode("1120011400")
		).thenThrow(
			new ComplexListApiException(
				"공동주택 단지 목록 API 호출에 실패했습니다."
			)
		);

		assertThatThrownBy(
			() -> service.collectByLegalDongCodes(
				List.of(
					"1117013100",
					"1120011400",
					"1120011500"
				)
			)
		)
			.isInstanceOf(ComplexListApiException.class)
			.hasMessageContaining(
				"공동주택 단지 목록 API 호출에 실패했습니다"
			);

		InOrder inOrder = inOrder(apiClient);
		inOrder.verify(apiClient)
			.fetchAllByLegalDongCode("1117013100");
		inOrder.verify(apiClient)
			.fetchAllByLegalDongCode("1120011400");

		verifyNoMoreInteractions(apiClient);
	}
}
