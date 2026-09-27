package com.geupjido.batch.complex.service;

import java.math.BigDecimal;
import java.time.LocalDate;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InOrder;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import com.geupjido.batch.complex.client.ComplexBasicInfoApiClient;
import com.geupjido.batch.complex.dto.ComplexBasicInfoApiItem;
import com.geupjido.batch.complex.exception.ComplexBasicInfoApiException;
import com.geupjido.batch.complex.exception.ComplexBasicInfoMappingException;
import com.geupjido.batch.complex.mapper.ComplexBasicInfoMapper;
import com.geupjido.complex.domain.Complex;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.Mockito.inOrder;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class ComplexBasicInfoCollectionServiceTest {

	@Mock
	private ComplexBasicInfoApiClient apiClient;

	@Mock
	private ComplexBasicInfoMapper mapper;

	@InjectMocks
	private ComplexBasicInfoCollectionService service;

	@Test
	void 단지_코드로_기본정보_한_건을_수집한다() {
		ComplexBasicInfoApiItem item =
			new ComplexBasicInfoApiItem(
				"A10027875",
				"괴정 경성스마트W아파트",
				"부산광역시 사하구 괴정동 258",
				"부산광역시 사하구 낙동대로 180",
				"3",
				new BigDecimal("182"),
				"20150806",
				"2638010100"
			);

		Complex complex = new Complex(
			"A10027875",
			"괴정 경성스마트W아파트",
			"부산광역시 사하구 괴정동 258",
			"부산광역시 사하구 낙동대로 180",
			182,
			3,
			LocalDate.of(2015, 8, 6)
		);

		when(apiClient.fetchByComplexCode("A10027875"))
			.thenReturn(item);
		when(mapper.map(item)).thenReturn(complex);

		Complex result =
			service.collectByComplexCode("A10027875");

		assertThat(result).isSameAs(complex);

		InOrder inOrder = inOrder(apiClient, mapper);
		inOrder.verify(apiClient)
			.fetchByComplexCode("A10027875");
		inOrder.verify(mapper).map(item);
	}

	@Test
	void 기본정보_API_호출이_실패하면_예외를_그대로_전달한다() {
		when(apiClient.fetchByComplexCode("A10027875"))
			.thenThrow(
				new ComplexBasicInfoApiException(
					"공동주택 단지 기본정보 API 호출에 실패했습니다."
				)
			);

		assertThatThrownBy(
			() -> service.collectByComplexCode("A10027875")
		)
			.isInstanceOf(ComplexBasicInfoApiException.class)
			.hasMessageContaining(
				"공동주택 단지 기본정보 API 호출에 실패했습니다"
			);

		verifyNoInteractions(mapper);
	}

	@Test
	void 기본정보_변환이_실패하면_예외를_그대로_전달한다() {
		ComplexBasicInfoApiItem item =
			new ComplexBasicInfoApiItem(
				"A10027875",
				"괴정 경성스마트W아파트",
				"부산광역시 사하구 괴정동 258",
				null,
				"세 개 동",
				new BigDecimal("182"),
				"20150806",
				"2638010100"
			);

		when(apiClient.fetchByComplexCode("A10027875"))
			.thenReturn(item);
		when(mapper.map(item))
			.thenThrow(
				new ComplexBasicInfoMappingException(
					"동 수를 정수로 변환할 수 없습니다."
				)
			);

		assertThatThrownBy(
			() -> service.collectByComplexCode("A10027875")
		)
			.isInstanceOf(
				ComplexBasicInfoMappingException.class
			)
			.hasMessageContaining(
				"동 수를 정수로 변환할 수 없습니다"
			);
	}
}
