package com.geupjido.batch.trade.service;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.stream.Collectors;

import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import com.geupjido.batch.trade.exception.ApartmentTradeMappingException;
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
				throw new ApartmentTradeMappingException(
					"API 거래의 시군구 또는 계약월이 요청과 다릅니다."
				);
			}
			groups.computeIfAbsent(SourceKey.of(entity), ignored -> new ArrayList<>())
				.add(new IndexedTrade(index, entity));
		}

		List<ApartmentTradeData> safeTrades = new ArrayList<>();
		List<List<IndexedTrade>> resolvedGroups = new ArrayList<>();
		int conflictRecords = 0;
		int conflictKeys = 0;
		int newConflictRecords = 0;
		for (List<IndexedTrade> group : groups.values()) {
			ApartmentTrade incoming = group.getFirst().entity();
			List<ApartmentTrade> stored = repository.findAllBySourceKey(
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
				if (group.size() == 2
					&& group.get(0).entity().isCanceled()
						!= group.get(1).entity().isCanceled()) {
					resolvedGroups.add(group);
				}
				continue;
			}
			conflictKeys++;
			conflictRecords += group.size();
			for (IndexedTrade item : group) {
				if (saveConflict(dealYearMonth, item,
					stored.isEmpty() ? null : stored.getFirst(), reason)) {
					newConflictRecords++;
				}
			}
		}

		ApartmentTradeImportResult imported = persistenceService.persist(safeTrades);
		for (List<IndexedTrade> group : resolvedGroups) {
			resolveConflict(districtCode, dealYearMonth, group.getFirst().entity());
		}
		return new ApartmentTradePartialImportResult(
			imported, conflictRecords, conflictKeys, newConflictRecords
		);
	}

	private static String conflictReason(
		List<IndexedTrade> group,
		List<ApartmentTrade> stored
	) {
		Set<Boolean> statuses = group.stream()
			.map(item -> item.entity().isCanceled())
			.collect(Collectors.toSet());
		if (statuses.size() > 1) {
			if (group.size() != 2) {
				return "SOURCE_STATUS_CONFLICT";
			}
			ApartmentTrade first = group.get(0).entity();
			ApartmentTrade second = group.get(1).entity();
			ApartmentTrade normal = first.isCanceled() ? second : first;
			ApartmentTrade canceled = first.isCanceled() ? first : second;
			if (!ApartmentTradeSourceIdentity.isRegisteredAfterCancellation(
				normal, canceled)) {
				return "SOURCE_STATUS_CONFLICT";
			}
		}
		Set<LocalDate> cancellationDates = group.stream()
			.map(item -> item.entity().getCancellationDate())
			.filter(java.util.Objects::nonNull)
			.collect(Collectors.toSet());
		if (cancellationDates.size() > 1) {
			return "SOURCE_CANCELLATION_DATE_MISMATCH";
		}
		Set<LocalDate> registrationDates = group.stream()
			.map(item -> item.entity().getRegistrationDate())
			.filter(java.util.Objects::nonNull)
			.collect(Collectors.toSet());
		if (registrationDates.size() > 1) {
			return "SOURCE_REGISTRATION_DATE_MISMATCH";
		}
		for (IndexedTrade item : group) {
			ApartmentTrade incoming = item.entity();
			List<ApartmentTrade> matching = stored.stream()
				.filter(trade -> trade.isCanceled() == incoming.isCanceled())
				.toList();
			if (matching.size() > 1) {
				return "REGISTRATION_DATE_MISMATCH";
			}
			if (!matching.isEmpty()) {
				ApartmentTrade current = matching.getFirst();
				if (incoming.isCanceled() && !current.getCancellationDate()
					.equals(incoming.getCancellationDate())) {
					return "CANCELLATION_DATE_MISMATCH";
				}
				if (current.getRegistrationDate() != null
					&& incoming.getRegistrationDate() != null
					&& !current.getRegistrationDate()
						.equals(incoming.getRegistrationDate())) {
					return "REGISTRATION_DATE_MISMATCH";
				}
				continue;
			}
			if (stored.size() == 1) {
				ApartmentTrade opposite = stored.getFirst();
				if (!incoming.isCanceled()) {
					if (!ApartmentTradeSourceIdentity
						.isRegisteredAfterCancellation(incoming, opposite)) {
						return "CANCELED_TO_NORMAL";
					}
				} else if (opposite.getRegistrationDate() != null
					&& opposite.getRegistrationDate().isAfter(
						incoming.getCancellationDate())
					&& !ApartmentTradeSourceIdentity
						.isRegisteredAfterCancellation(opposite, incoming)) {
					return "CANCELED_BEFORE_REGISTRATION";
				}
			}
		}
		return null;
	}

	private void resolveConflict(
		String districtCode, String dealYearMonth, ApartmentTrade trade
	) {
		jdbcTemplate.update("""
			UPDATE apartment_trade_conflict
			SET review_status = 'RESOLVED', observed_at = CURRENT_TIMESTAMP
			WHERE district_code = ? AND deal_year_month = ?
			  AND legal_dong_name = ? AND raw_apartment_name = ?
			  AND deal_date = ? AND deal_amount = ?
			  AND exclusive_area IS NOT DISTINCT FROM ?
			  AND floor IS NOT DISTINCT FROM ?
			  AND review_status = 'OPEN'
			""", districtCode, dealYearMonth, trade.getLegalDongName(),
			trade.getRawApartmentName(), trade.getDealDate(),
			trade.getDealAmountTenThousandWon(), trade.getExclusiveArea(),
			trade.getFloor());
	}

	private boolean saveConflict(
		String dealYearMonth,
		IndexedTrade item,
		ApartmentTrade stored,
		String reason
	) {
		ApartmentTrade trade = item.entity();
		List<Long> insertedIds = jdbcTemplate.query("""
			INSERT INTO apartment_trade_conflict_seen (
				district_code, deal_year_month, legal_dong_name,
				raw_apartment_name, lot_number, apartment_dong, build_year,
				deal_date, deal_amount, exclusive_area, floor, dealing_type,
				is_canceled, cancellation_date, registration_date
			) VALUES (?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?)
			ON CONFLICT DO NOTHING
			RETURNING id
			""",
			(rs, rowNum) -> rs.getLong(1),
			trade.getDistrictCode(), dealYearMonth,
			trade.getLegalDongName(), trade.getRawApartmentName(),
			trade.getLotNumber(), trade.getApartmentDong(), trade.getBuildYear(),
			trade.getDealDate(), trade.getDealAmountTenThousandWon(),
			trade.getExclusiveArea(), trade.getFloor(), trade.getDealingType(),
			trade.isCanceled(), trade.getCancellationDate(),
			trade.getRegistrationDate());
		Long sourceSeenId = insertedIds.isEmpty()
			? jdbcTemplate.queryForObject("""
				SELECT id FROM apartment_trade_conflict_seen
				WHERE district_code = ? AND deal_year_month = ?
				  AND legal_dong_name = ? AND raw_apartment_name = ?
				  AND lot_number IS NOT DISTINCT FROM ?
				  AND apartment_dong IS NOT DISTINCT FROM ?
				  AND build_year IS NOT DISTINCT FROM ?
				  AND deal_date = ? AND deal_amount = ?
				  AND exclusive_area IS NOT DISTINCT FROM ?
				  AND floor IS NOT DISTINCT FROM ?
				  AND dealing_type IS NOT DISTINCT FROM ?
				  AND is_canceled = ?
				  AND cancellation_date IS NOT DISTINCT FROM ?
				  AND registration_date IS NOT DISTINCT FROM ?
				""", Long.class, trade.getDistrictCode(), dealYearMonth,
				trade.getLegalDongName(), trade.getRawApartmentName(),
				trade.getLotNumber(), trade.getApartmentDong(),
				trade.getBuildYear(), trade.getDealDate(),
				trade.getDealAmountTenThousandWon(), trade.getExclusiveArea(),
				trade.getFloor(), trade.getDealingType(), trade.isCanceled(),
				trade.getCancellationDate(), trade.getRegistrationDate())
			: insertedIds.getFirst();
		jdbcTemplate.update("""
			INSERT INTO apartment_trade_conflict (
				district_code, deal_year_month, source_ordinal, source_seen_id,
				conflict_reason, existing_trade_id, legal_dong_name,
				raw_apartment_name, lot_number, apartment_dong, build_year,
				deal_date, deal_amount, exclusive_area, floor, dealing_type,
				is_canceled, cancellation_date, registration_date
			) VALUES (?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?)
			ON CONFLICT (source_seen_id) WHERE review_status = 'OPEN'
			DO UPDATE SET source_ordinal = EXCLUDED.source_ordinal,
				conflict_reason = EXCLUDED.conflict_reason,
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
				registration_date = EXCLUDED.registration_date,
				observed_at = CURRENT_TIMESTAMP
			""",
			trade.getDistrictCode(), dealYearMonth, item.ordinal(),
			sourceSeenId, reason,
			stored == null ? null : stored.getId(), trade.getLegalDongName(),
			trade.getRawApartmentName(), trade.getLotNumber(),
			trade.getApartmentDong(), trade.getBuildYear(), trade.getDealDate(),
			trade.getDealAmountTenThousandWon(), trade.getExclusiveArea(),
			trade.getFloor(), trade.getDealingType(), trade.isCanceled(),
			trade.getCancellationDate(), trade.getRegistrationDate()
		);
		return !insertedIds.isEmpty();
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
