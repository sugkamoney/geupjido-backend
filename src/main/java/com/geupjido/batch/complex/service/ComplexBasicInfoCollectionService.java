package com.geupjido.batch.complex.service;

import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.stereotype.Service;

import com.geupjido.batch.complex.client.ComplexBasicInfoApiClient;
import com.geupjido.batch.complex.dto.ComplexBasicInfoApiItem;
import com.geupjido.batch.complex.mapper.ComplexBasicInfoMapper;
import com.geupjido.complex.domain.Complex;

/**
 * 단지 코드를 기준으로 공동주택 기본정보 한 건을 수집한다.
 */
@Service
@ConditionalOnProperty(
	prefix = "app.external.complex-basic-info",
	name = "enabled",
	havingValue = "true"
)
public class ComplexBasicInfoCollectionService {

	private final ComplexBasicInfoApiClient apiClient;
	private final ComplexBasicInfoMapper mapper;

	public ComplexBasicInfoCollectionService(
		ComplexBasicInfoApiClient apiClient,
		ComplexBasicInfoMapper mapper
	) {
		this.apiClient = apiClient;
		this.mapper = mapper;
	}

	public Complex collectByComplexCode(String complexCode) {
		ComplexBasicInfoApiItem item =
			apiClient.fetchByComplexCode(complexCode);

		return mapper.map(item);
	}
}
