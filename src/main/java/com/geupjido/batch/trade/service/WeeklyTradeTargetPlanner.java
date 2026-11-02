package com.geupjido.batch.trade.service;

import java.nio.file.Path;
import java.time.YearMonth;
import java.time.format.DateTimeFormatter;
import java.util.ArrayList;
import java.util.List;

import org.springframework.stereotype.Component;

import com.geupjido.batch.location.reader.LocationMappingReader;

/** 주간 수집에서 다시 조회할 계약월과 서비스 대상 시군구를 정한다. */
@Component
public class WeeklyTradeTargetPlanner {

	private static final DateTimeFormatter MONTH_FORMAT =
		DateTimeFormatter.ofPattern("yyyyMM");

	private final LocationMappingReader mappingReader;

	public WeeklyTradeTargetPlanner(LocationMappingReader mappingReader) {
		this.mappingReader = mappingReader;
	}

	public List<Target> plan(
		Path mappingPath, YearMonth currentMonth, int monthsToCollect
	) {
		if (mappingPath == null || currentMonth == null || monthsToCollect <= 0) {
			throw new IllegalArgumentException(
				"매핑 경로, 기준 계약월, 양수 조회 개월 수가 필요합니다."
			);
		}
		List<String> districtCodes = mappingReader.read(mappingPath)
			.zones().stream()
			.map(zone -> zone.cityId())
			.distinct()
			.sorted()
			.toList();
		if (districtCodes.isEmpty()) {
			throw new IllegalStateException("권역 매핑에 수집할 시군구가 없습니다.");
		}

		List<Target> targets = new ArrayList<>();
		for (int offset = monthsToCollect - 1; offset >= 0; offset--) {
			String month = currentMonth.minusMonths(offset).format(MONTH_FORMAT);
			for (String districtCode : districtCodes) {
				targets.add(new Target(districtCode, month));
			}
		}
		return List.copyOf(targets);
	}

	public record Target(String districtCode, String dealYearMonth) {
	}
}
