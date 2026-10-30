package com.geupjido.batch.complex.service;

import java.util.ArrayList;
import java.util.List;

import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.data.domain.PageRequest;
import org.springframework.stereotype.Service;
import org.springframework.transaction.support.TransactionTemplate;

import com.geupjido.batch.complex.client.ComplexBasicInfoApiClient;
import com.geupjido.batch.complex.dto.ComplexBasicInfoApiItem;
import com.geupjido.batch.complex.exception.ComplexBasicInfoApiException;
import com.geupjido.complex.domain.Complex;
import com.geupjido.complex.repository.ComplexRepository;
import com.geupjido.trade.repository.ApartmentTradeRepository;

/** 기존 단지의 분양형태를 보충하고 확인된 임대 전용 단지는 제거한다. */
@Service
@ConditionalOnProperty(prefix = "app.external.complex-basic-info",
	name = "enabled", havingValue = "true")
public class ComplexSaleTypeBackfillService {
	private static final int API_UNAVAILABLE_STOP_THRESHOLD = 3;

	private final ComplexRepository repository;
	private final ApartmentTradeRepository tradeRepository;
	private final ComplexBasicInfoApiClient apiClient;
	private final ComplexApiRetry retry;
	private final TransactionTemplate transactionTemplate;

	public ComplexSaleTypeBackfillService(
		ComplexRepository repository, ApartmentTradeRepository tradeRepository,
		ComplexBasicInfoApiClient apiClient,
		ComplexApiRetry retry, TransactionTemplate transactionTemplate
	) {
		this.repository = repository;
		this.tradeRepository = tradeRepository;
		this.apiClient = apiClient;
		this.retry = retry;
		this.transactionTemplate = transactionTemplate;
	}

	public Result backfill(String afterId, int maxCount) {
		return backfill(afterId, maxCount, false);
	}

	public Result backfill(String afterId, int maxCount,
		boolean onlyUnpriced) {
		if (afterId == null || maxCount < 1 || maxCount > 5000) {
			throw new IllegalArgumentException(
				"분양형태 적재 시작 ID와 1~5000건의 실행 한도가 필요합니다."
			);
		}
		PageRequest page = PageRequest.of(0, maxCount);
		List<String> ids = onlyUnpriced
			? repository.findIdsMissingSaleTypeAndPriceAfterId(afterId, page)
			: repository.findIdsMissingSaleTypeAfterId(afterId, page);
		int updated = 0;
		int excluded = 0;
		List<String> failed = new ArrayList<>();
		String lastId = afterId;
		boolean quotaExceeded = false;
		boolean apiUnavailable = false;
		int consecutiveUnavailable = 0;
		for (String id : ids) {
			FetchResult result = fetch(id);
			if ("22".equals(result.errorCode())) {
				quotaExceeded = true;
				break;
			}
			if (result.item() == null) {
				failed.add(id);
				lastId = id;
				consecutiveUnavailable = "04".equals(result.errorCode())
					? consecutiveUnavailable + 1 : 0;
				if (consecutiveUnavailable
					>= API_UNAVAILABLE_STOP_THRESHOLD) {
					apiUnavailable = true;
					break;
				}
				continue;
			}
			consecutiveUnavailable = 0;
			ComplexBasicInfoApiItem item = result.item();
			if (!id.equals(item.kaptCode()) || item.codeSaleNm() == null
				|| item.codeSaleNm().isBlank()) {
				failed.add(id);
				lastId = id;
				continue;
			}
			Boolean isExcluded = transactionTemplate.execute(ignored -> {
				Complex complex = repository.findById(id).orElseThrow();
				complex.updateSaleType(item.codeSaleNm());
				if (complex.isRentalOnly() || complex.isStaffHousing()) {
					tradeRepository.detachLinksForComplex(id);
					repository.delete(complex);
					return true;
				}
				return false;
			});
			if (Boolean.TRUE.equals(isExcluded)) {
				excluded++;
			} else {
				updated++;
			}
			lastId = id;
		}
		return new Result(ids.size(), updated, excluded,
			List.copyOf(failed), lastId, quotaExceeded, apiUnavailable);
	}

	private FetchResult fetch(String id) {
		try {
			ComplexBasicInfoApiItem item = retry.execute(
				() -> apiClient.fetchByComplexCode(id),
				error -> error instanceof ComplexBasicInfoApiException apiError
					&& "04".equals(apiError.resultCode())
			);
			return new FetchResult(item, null);
		} catch (ComplexBasicInfoApiException exception) {
			return new FetchResult(null, exception.resultCode());
		}
	}

	private record FetchResult(ComplexBasicInfoApiItem item, String errorCode) {
	}

	public record Result(int selected, int updated, int excluded,
		List<String> failedIds, String lastId, boolean quotaExceeded,
		boolean apiUnavailable) {
	}
}
