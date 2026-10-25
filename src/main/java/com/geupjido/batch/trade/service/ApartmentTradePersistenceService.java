package com.geupjido.batch.trade.service;

import java.util.List;
import java.util.Optional;

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
			Optional<ApartmentTrade> found = repository.findBySourceKey(
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

			ApartmentTrade stored = found.get();
			if (stored.isCanceled()) {
				if (!incoming.isCanceled()) {
					throw new IllegalStateException(
						"해제된 거래가 정상 거래로 다시 제공되었습니다."
					);
				}
				stored.markCanceled(incoming.getCancellationDate());
				skipped++;
				continue;
			}

			if (incoming.isCanceled()) {
				stored.markCanceled(incoming.getCancellationDate());
				updated++;
			} else {
				skipped++;
			}
		}

		return new ApartmentTradeImportResult(
			trades.size(), created, skipped, updated
		);
	}
}
