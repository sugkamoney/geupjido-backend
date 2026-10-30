package com.geupjido.batch.trade.service;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.Set;
import java.util.stream.Collectors;

import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import com.geupjido.batch.trade.mapper.ApartmentTradeEntityMapper;
import com.geupjido.batch.trade.model.ApartmentTradeData;
import com.geupjido.batch.trade.model.ApartmentTradeImportResult;
import com.geupjido.batch.trade.model.ApartmentTradePartialImportResult;
import com.geupjido.trade.domain.ApartmentTrade;
import com.geupjido.trade.repository.ApartmentTradeRepository;

/** 소급 수집에서 충돌한 식별 키만 분리하고 나머지는 기존 저장 규칙으로 적재한다. */
@Service
public class ApartmentTradeBackfillPersistenceService {

	private final ApartmentTradeRepository repository;
	private final ApartmentTradeEntityMapper mapper;
	private final ApartmentTradePersistenceService persistenceService;
	private final JdbcTemplate jdbcTemplate;

	public ApartmentTradeBackfillPersistenceService(
		ApartmentTradeRepository repository,
		ApartmentTradeEntityMapper mapper,
		ApartmentTradePersistenceService persistenceService,
		JdbcTemplate jdbcTemplate
	) {
		this.repository = repository;
		this.mapper = mapper;
		this.persistenceService = persistenceService;
		this.jdbcTemplate = jdbcTemplate;
	}

	@Transactional
	public ApartmentTradePartialImportResult persist(
		String districtCode,
		String dealYearMonth,
		List<ApartmentTradeData> trades
	) {
		Map<SourceKey, List<IndexedTrade>> groups = new LinkedHashMap<>();
		for (int index = 0; index < trades.size(); index++) {
			ApartmentTrade entity = mapper.map(trades.get(index));
			if (!districtCode.equals(entity.getDistrictCode())
				|| !dealYearMonth.equals(entity.getDealDate()
					.format(java.time.format.DateTimeFormatter.ofPattern("yyyyMM")))) {
				throw new IllegalArgumentException(
					"API 거래의 시군구 또는 계약월이 요청과 다릅니다."
				);
			}
			groups.computeIfAbsent(SourceKey.of(entity), ignored -> new ArrayList<>())
				.add(new IndexedTrade(index, entity));
		}

		List<ApartmentTradeData> safeTrades = new ArrayList<>();
		int conflictRecords = 0;
		int conflictKeys = 0;
		for (List<IndexedTrade> group : groups.values()) {
			ApartmentTrade incoming = group.getFirst().entity();
			Optional<ApartmentTrade> stored = repository.findBySourceKey(
				incoming.getDistrictCode(), incoming.getLegalDongName(),
				incoming.getRawApartmentName(), incoming.getDealDate(),
				incoming.getExclusiveArea(), incoming.getFloor(),
				incoming.getDealAmountTenThousandWon()
			);
			String reason = conflictReason(group, stored);
			if (reason == null) {
				for (IndexedTrade item : group) {
					safeTrades.add(trades.get(item.ordinal()));
				}
				continue;
			}
			conflictKeys++;
			conflictRecords += group.size();
			for (IndexedTrade item : group) {
				saveConflict(dealYearMonth, item, stored.orElse(null), reason);
			}
		}

		ApartmentTradeImportResult imported = persistenceService.persist(safeTrades);
		return new ApartmentTradePartialImportResult(
			imported, conflictRecords, conflictKeys
		);
	}

	private static String conflictReason(
		List<IndexedTrade> group,
		Optional<ApartmentTrade> stored
	) {
		Set<Boolean> statuses = group.stream()
			.map(item -> item.entity().isCanceled())
			.collect(Collectors.toSet());
		if (statuses.size() > 1) {
			return "SOURCE_STATUS_CONFLICT";
		}
		Set<LocalDate> cancellationDates = group.stream()
			.map(item -> item.entity().getCancellationDate())
			.collect(Collectors.toSet());
		if (cancellationDates.size() > 1) {
			return "SOURCE_CANCELLATION_DATE_MISMATCH";
		}
		if (stored.isPresent() && stored.get().isCanceled()) {
			ApartmentTrade incoming = group.getFirst().entity();
			if (!incoming.isCanceled()) {
				return "CANCELED_TO_NORMAL";
			}
			if (!stored.get().getCancellationDate()
				.equals(incoming.getCancellationDate())) {
				return "CANCELLATION_DATE_MISMATCH";
			}
		}
		return null;
	}

	private void saveConflict(
		String dealYearMonth,
		IndexedTrade item,
		ApartmentTrade stored,
		String reason
	) {
		ApartmentTrade trade = item.entity();
		jdbcTemplate.update("""
			INSERT INTO apartment_trade_conflict (
				district_code, deal_year_month, source_ordinal,
				conflict_reason, existing_trade_id, legal_dong_name,
				raw_apartment_name, lot_number, apartment_dong, build_year,
				deal_date, deal_amount, exclusive_area, floor, dealing_type,
				is_canceled, cancellation_date
			) VALUES (?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?)
			ON CONFLICT (district_code, deal_year_month, source_ordinal)
			DO UPDATE SET conflict_reason = EXCLUDED.conflict_reason,
				review_status = 'OPEN',
				existing_trade_id = EXCLUDED.existing_trade_id,
				legal_dong_name = EXCLUDED.legal_dong_name,
				raw_apartment_name = EXCLUDED.raw_apartment_name,
				lot_number = EXCLUDED.lot_number,
				apartment_dong = EXCLUDED.apartment_dong,
				build_year = EXCLUDED.build_year,
				deal_date = EXCLUDED.deal_date,
				deal_amount = EXCLUDED.deal_amount,
				exclusive_area = EXCLUDED.exclusive_area,
				floor = EXCLUDED.floor,
				dealing_type = EXCLUDED.dealing_type,
				is_canceled = EXCLUDED.is_canceled,
				cancellation_date = EXCLUDED.cancellation_date,
				observed_at = CURRENT_TIMESTAMP
			""",
			trade.getDistrictCode(), dealYearMonth, item.ordinal(), reason,
			stored == null ? null : stored.getId(), trade.getLegalDongName(),
			trade.getRawApartmentName(), trade.getLotNumber(),
			trade.getApartmentDong(), trade.getBuildYear(), trade.getDealDate(),
			trade.getDealAmountTenThousandWon(), trade.getExclusiveArea(),
			trade.getFloor(), trade.getDealingType(), trade.isCanceled(),
			trade.getCancellationDate()
		);
	}

	private record IndexedTrade(int ordinal, ApartmentTrade entity) {
	}

	private record SourceKey(
		String districtCode,
		String legalDongName,
		String rawApartmentName,
		LocalDate dealDate,
		BigDecimal exclusiveArea,
		Short floor,
		long dealAmount
	) {
		private static SourceKey of(ApartmentTrade trade) {
			BigDecimal area = trade.getExclusiveArea();
			return new SourceKey(
				trade.getDistrictCode(), trade.getLegalDongName(),
				trade.getRawApartmentName(), trade.getDealDate(),
				area == null ? null : area.stripTrailingZeros(),
				trade.getFloor(), trade.getDealAmountTenThousandWon()
			);
		}
	}
}
