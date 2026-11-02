package com.geupjido.batch.trade.service;

import java.util.List;

import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import com.geupjido.batch.trade.mapper.ApartmentTradeEntityMapper;
import com.geupjido.batch.trade.model.ApartmentTradeData;
import com.geupjido.batch.trade.model.ApartmentTradeImportResult;
import com.geupjido.trade.domain.ApartmentTrade;
import com.geupjido.trade.repository.ApartmentTradeRepository;

@Service
public class ApartmentTradePersistenceService {
	private final ApartmentTradeRepository repository;
	private final ApartmentTradeEntityMapper mapper;

	public ApartmentTradePersistenceService(
		ApartmentTradeRepository repository,
		ApartmentTradeEntityMapper mapper
	) {
		this.repository = repository;
		this.mapper = mapper;
	}

	@Transactional
	public ApartmentTradeImportResult persist(List<ApartmentTradeData> trades) {
		int created = 0;
		int skipped = 0;
		int updated = 0;

		for (ApartmentTradeData data : trades) {
			ApartmentTrade incoming = mapper.map(data);
			List<ApartmentTrade> found = repository.findAllBySourceKey(
				incoming.getDistrictCode(),
				incoming.getLegalDongName(),
				incoming.getRawApartmentName(),
				incoming.getDealDate(),
				incoming.getExclusiveArea(),
				incoming.getFloor(),
				incoming.getDealAmountTenThousandWon()
			);

			if (found.isEmpty()) {
				repository.save(incoming);
				created++;
				continue;
			}

			List<ApartmentTrade> sameStatus = found.stream()
				.filter(trade -> trade.isCanceled() == incoming.isCanceled())
				.toList();
			if (sameStatus.size() > 1) {
				throw new IllegalStateException(
					"같은 식별 키와 상태의 기존 거래가 여러 건입니다."
				);
			}
			if (!sameStatus.isEmpty()) {
				ApartmentTrade stored = sameStatus.getFirst();
				if (incoming.isCanceled()) {
				stored.markCanceled(incoming.getCancellationDate());
				}
				if (stored.fillRegistrationDate(incoming.getRegistrationDate())) {
					updated++;
				} else {
					skipped++;
				}
				continue;
			}

			if (found.size() != 1) {
				throw new IllegalStateException("기존 거래의 상태가 모호합니다.");
			}
			ApartmentTrade opposite = found.getFirst();
			if (!incoming.isCanceled()) {
				if (!ApartmentTradeSourceIdentity.isRegisteredAfterCancellation(
					incoming, opposite)) {
					throw new IllegalStateException(
						"해제된 거래가 정상 거래로 다시 제공되었습니다."
					);
				}
				repository.save(incoming);
				created++;
				continue;
			}
			if (ApartmentTradeSourceIdentity.isRegisteredAfterCancellation(
				opposite, incoming)) {
				repository.save(incoming);
				created++;
				continue;
			}
			if (opposite.getRegistrationDate() != null
				&& opposite.getRegistrationDate().isAfter(
					incoming.getCancellationDate())) {
				throw new IllegalStateException(
					"등기일보다 앞서 해제된 같은 식별 키의 거래입니다."
				);
			}
			opposite.markCanceled(incoming.getCancellationDate());
			opposite.fillRegistrationDate(incoming.getRegistrationDate());
			updated++;
		}

		return new ApartmentTradeImportResult(
			trades.size(), created, skipped, updated
		);
	}
}
