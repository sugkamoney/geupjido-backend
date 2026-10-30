package com.geupjido.batch.trade.service;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

import org.springframework.data.domain.Sort;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import com.geupjido.batch.trade.model.ComplexRepresentativePriceResult;
import com.geupjido.complex.domain.Complex;
import com.geupjido.complex.domain.RepresentativePriceSource;
import com.geupjido.complex.repository.ComplexRepository;
import com.geupjido.trade.domain.ApartmentTrade;
import com.geupjido.trade.repository.ApartmentTradeRepository;
import com.geupjido.trade.repository.ApartmentPresaleTradeRepository;
import com.geupjido.trade.repository.ApartmentPresaleTradeRepository.Candidate;

/** 검증된 정상 거래의 실제 가격을 선택하고 ㎡당 가격으로 잠정 순위를 계산한다. */
@Service
public class ComplexRepresentativePriceService {

	private static final int QUERY_BATCH_SIZE = 200;
	private static final BigDecimal AREA_START = BigDecimal.valueOf(58);
	private static final BigDecimal AREA_END = BigDecimal.valueOf(61);
	private static final BigDecimal FALLBACK_AREA_START = BigDecimal.valueOf(44);
	private static final BigDecimal FALLBACK_AREA_END = BigDecimal.valueOf(65);
	private static final BigDecimal REFERENCE_AREA = BigDecimal.valueOf(59);

	private final ComplexRepository complexRepository;
	private final ApartmentTradeRepository tradeRepository;
	private final ApartmentPresaleTradeRepository presaleTradeRepository;

	public ComplexRepresentativePriceService(
		ComplexRepository complexRepository,
		ApartmentTradeRepository tradeRepository,
		ApartmentPresaleTradeRepository presaleTradeRepository
	) {
		this.complexRepository = complexRepository;
		this.tradeRepository = tradeRepository;
		this.presaleTradeRepository = presaleTradeRepository;
	}

	@Transactional
	public ComplexRepresentativePriceResult recalculateAll() {
		List<Complex> complexes = complexRepository.findAll(Sort.by("id"));
		Map<String, SelectedTrade> selectedByComplexId = new HashMap<>();

		for (int from = 0; from < complexes.size(); from += QUERY_BATCH_SIZE) {
			int to = Math.min(from + QUERY_BATCH_SIZE, complexes.size());
			List<String> ids = complexes.subList(from, to).stream()
				.map(Complex::getId).toList();
			selectCandidates(selectedByComplexId,
				tradeRepository.findRepresentativeCandidates(
				ids, AREA_START, AREA_END
			));
		}
		List<String> fallbackIds = missingIds(complexes, selectedByComplexId);
		for (int from = 0; from < fallbackIds.size(); from += QUERY_BATCH_SIZE) {
			int to = Math.min(from + QUERY_BATCH_SIZE, fallbackIds.size());
			selectCandidates(selectedByComplexId,
				tradeRepository.findFallbackRepresentativeCandidates(
					fallbackIds.subList(from, to), FALLBACK_AREA_START,
					FALLBACK_AREA_END
				));
		}
		selectDirectCandidates(
			missingIds(complexes, selectedByComplexId),
			AREA_START, AREA_END, false, selectedByComplexId
		);
		selectDirectCandidates(
			missingIds(complexes, selectedByComplexId),
			FALLBACK_AREA_START, FALLBACK_AREA_END, true,
			selectedByComplexId
		);
		selectOtherAreaCandidates(
			missingIds(complexes, selectedByComplexId),
			"중개거래", selectedByComplexId
		);
		selectOtherAreaCandidates(
			missingIds(complexes, selectedByComplexId),
			"직거래", selectedByComplexId
		);
		selectRightsCandidates(
			missingIds(complexes, selectedByComplexId), selectedByComplexId
		);

		int priced = 0;
		for (Complex complex : complexes) {
			SelectedTrade selected = complex.isRentalOnly()
				? null : selectedByComplexId.get(complex.getId());
			if (selected == null) {
				complex.updateRepresentativeTrade(null, null, null);
			} else {
				complex.updateRepresentativeTrade(
					selected.price(), selected.area(), selected.date(),
					selected.source()
				);
				priced++;
			}
			complex.updateProvisionalZoneRank(null);
		}
		int ranked = assignProvisionalRanks(complexes);
		return new ComplexRepresentativePriceResult(
			complexes.size(), priced, ranked, complexes.size() - priced
		);
	}

	private void selectRightsCandidates(
		List<String> ids,
		Map<String, SelectedTrade> selectedByComplexId
	) {
		for (int from = 0; from < ids.size(); from += QUERY_BATCH_SIZE) {
			int to = Math.min(from + QUERY_BATCH_SIZE, ids.size());
			List<Candidate> candidates = presaleTradeRepository
				.findRepresentativeCandidates(ids.subList(from, to));
			int index = 0;
			while (index < candidates.size()) {
				Candidate first = candidates.get(index);
				int groupEnd = index + 1;
				while (groupEnd < candidates.size()
					&& sameSelectionGroup(first, candidates.get(groupEnd))) {
					groupEnd++;
				}
				int selectedIndex = index + (groupEnd - index) / 2;
				long selectedAmount = candidates.get(selectedIndex).amount();
				while (selectedIndex > index
					&& candidates.get(selectedIndex - 1).amount()
						== selectedAmount) {
					selectedIndex--;
				}
				Candidate selected = candidates.get(selectedIndex);
				selectedByComplexId.put(first.complexId(), new SelectedTrade(
					Math.toIntExact(selected.amount()), selected.area(),
					selected.dealDate(),
					RepresentativePriceSource.PRESALE_OR_OCCUPANCY_RIGHT
				));
				while (groupEnd < candidates.size()
					&& candidates.get(groupEnd).complexId()
						.equals(first.complexId())) {
					groupEnd++;
				}
				index = groupEnd;
			}
		}
	}

	private static boolean sameSelectionGroup(Candidate left, Candidate right) {
		if (!left.complexId().equals(right.complexId())
			|| !left.dealDate().equals(right.dealDate())) {
			return false;
		}
		int leftTier = areaTier(left.area());
		int rightTier = areaTier(right.area());
		return leftTier == rightTier && (leftTier != 2
			|| left.area().subtract(REFERENCE_AREA).abs().compareTo(
				right.area().subtract(REFERENCE_AREA).abs()) == 0);
	}

	private static int areaTier(BigDecimal area) {
		if (area.compareTo(AREA_START) >= 0
			&& area.compareTo(AREA_END) < 0) {
			return 0;
		}
		if (area.compareTo(FALLBACK_AREA_START) >= 0
			&& area.compareTo(FALLBACK_AREA_END) <= 0) {
			return 1;
		}
		return 2;
	}

	private void selectOtherAreaCandidates(
		List<String> ids,
		String dealingType,
		Map<String, SelectedTrade> selectedByComplexId
	) {
		for (int from = 0; from < ids.size(); from += QUERY_BATCH_SIZE) {
			int to = Math.min(from + QUERY_BATCH_SIZE, ids.size());
			List<ApartmentTrade> candidates =
				tradeRepository.findOtherAreaRepresentativeCandidates(
					ids.subList(from, to), dealingType
				);
			List<ApartmentTrade> closestCandidates = new ArrayList<>();
			int index = 0;
			while (index < candidates.size()) {
				ApartmentTrade first = candidates.get(index);
				String complexId = first.getComplex().getId();
				BigDecimal distance = first.getExclusiveArea()
					.subtract(REFERENCE_AREA).abs();
				LocalDate latestDate = first.getDealDate();
				while (index < candidates.size()
					&& candidates.get(index).getComplex().getId()
						.equals(complexId)) {
					ApartmentTrade candidate = candidates.get(index);
					if (candidate.getExclusiveArea()
						.subtract(REFERENCE_AREA).abs()
						.compareTo(distance) == 0
						&& candidate.getDealDate().equals(latestDate)) {
						closestCandidates.add(candidate);
					}
					index++;
				}
			}
			selectCandidates(selectedByComplexId, closestCandidates);
		}
	}

	private void selectDirectCandidates(
		List<String> ids,
		BigDecimal areaStart,
		BigDecimal areaEnd,
		boolean includeAreaEnd,
		Map<String, SelectedTrade> selectedByComplexId
	) {
		for (int from = 0; from < ids.size(); from += QUERY_BATCH_SIZE) {
			int to = Math.min(from + QUERY_BATCH_SIZE, ids.size());
			selectCandidates(selectedByComplexId,
				tradeRepository.findDirectRepresentativeCandidates(
					ids.subList(from, to), areaStart, areaEnd,
					includeAreaEnd
				));
		}
	}

	private static List<String> missingIds(
		List<Complex> complexes,
		Map<String, SelectedTrade> selectedByComplexId
	) {
		return complexes.stream()
			.filter(complex -> !complex.isRentalOnly())
			.map(Complex::getId)
			.filter(id -> !selectedByComplexId.containsKey(id))
			.toList();
	}

	private static void selectCandidates(
		Map<String, SelectedTrade> selectedByComplexId,
		List<ApartmentTrade> candidates
	) {
		int from = 0;
		while (from < candidates.size()) {
			ApartmentTrade first = candidates.get(from);
			String complexId = first.getComplex().getId();
			LocalDate latestDate = first.getDealDate();
			int to = from + 1;
			while (to < candidates.size()
				&& candidates.get(to).getComplex().getId().equals(complexId)
				&& candidates.get(to).getDealDate().equals(latestDate)) {
				to++;
			}
			int selectedIndex = from + (to - from) / 2;
			long selectedAmount = candidates.get(selectedIndex)
				.getDealAmountTenThousandWon();
			while (selectedIndex > from && candidates.get(selectedIndex - 1)
				.getDealAmountTenThousandWon() == selectedAmount) {
				selectedIndex--;
			}
			ApartmentTrade selected = candidates.get(selectedIndex);
			selectedByComplexId.put(complexId, new SelectedTrade(
				Math.toIntExact(selected.getDealAmountTenThousandWon()),
				selected.getExclusiveArea(), selected.getDealDate(),
				RepresentativePriceSource.APARTMENT_SALE
			));
			while (to < candidates.size()
				&& candidates.get(to).getComplex().getId().equals(complexId)) {
				to++;
			}
			from = to;
		}
	}

	private static int assignProvisionalRanks(List<Complex> complexes) {
		Map<String, List<Complex>> byZone = new HashMap<>();
		for (Complex complex : complexes) {
			if (complex.getZone() != null && complex.getPrice() != null) {
				byZone.computeIfAbsent(complex.getZone().getId(),
					ignored -> new ArrayList<>()).add(complex);
			}
		}
		int ranked = 0;
		Comparator<Complex> order = ComplexRepresentativePriceService
			::compareUnitPriceDescending;
		order = order
			.thenComparing(Complex::getLastDealDate,
				Comparator.reverseOrder())
			.thenComparing(Complex::getId);
		for (List<Complex> zoneComplexes : byZone.values()) {
			zoneComplexes.sort(order);
			Complex previous = null;
			int rank = 0;
			for (int index = 0; index < zoneComplexes.size(); index++) {
				Complex complex = zoneComplexes.get(index);
				if (previous == null
					|| compareUnitPriceDescending(previous, complex) != 0) {
					rank = index + 1;
				}
				complex.updateProvisionalZoneRank(rank);
				previous = complex;
				ranked++;
			}
		}
		return ranked;
	}

	private static int compareUnitPriceDescending(Complex left, Complex right) {
		return BigDecimal.valueOf(right.getPrice())
			.multiply(left.getRepresentativeArea())
			.compareTo(BigDecimal.valueOf(left.getPrice())
				.multiply(right.getRepresentativeArea()));
	}

	private record SelectedTrade(int price, BigDecimal area, LocalDate date,
		RepresentativePriceSource source) {
	}
}
