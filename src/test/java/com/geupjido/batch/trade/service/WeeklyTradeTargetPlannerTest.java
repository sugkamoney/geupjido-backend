package com.geupjido.batch.trade.service;

import java.math.BigDecimal;
import java.nio.file.Path;
import java.time.YearMonth;
import java.util.List;

import org.junit.jupiter.api.Test;

import com.geupjido.batch.location.model.LocationMapping;
import com.geupjido.batch.location.model.LocationMapping.ZoneDefinition;
import com.geupjido.batch.location.reader.LocationMappingReader;
import com.geupjido.batch.trade.service.WeeklyTradeTargetPlanner.Target;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

class WeeklyTradeTargetPlannerTest {

	@Test
	void 연말을_넘는_최근_계약월과_중복_없는_시군구를_선택한다() {
		LocationMappingReader mappingReader = mock(LocationMappingReader.class);
		Path mappingPath = Path.of("mapping.json");
		when(mappingReader.read(mappingPath)).thenReturn(new LocationMapping(
			List.of(), List.of(), List.of(
				zone("zone-a", "11170"),
				zone("zone-b", "11110"),
				zone("zone-c", "11170")
			)
		));
		WeeklyTradeTargetPlanner planner =
			new WeeklyTradeTargetPlanner(mappingReader);

		assertThat(planner.plan(mappingPath, YearMonth.of(2027, 1), 3))
			.containsExactly(
				new Target("11110", "202611"),
				new Target("11170", "202611"),
				new Target("11110", "202612"),
				new Target("11170", "202612"),
				new Target("11110", "202701"),
				new Target("11170", "202701")
			);
	}

	@Test
	void 조회_개월_수가_양수가_아니면_거부한다() {
		WeeklyTradeTargetPlanner planner =
			new WeeklyTradeTargetPlanner(mock(LocationMappingReader.class));

		assertThatThrownBy(() -> planner.plan(
			Path.of("mapping.json"), YearMonth.of(2026, 11), 0
		)).isInstanceOf(IllegalArgumentException.class);
	}

	private static ZoneDefinition zone(String id, String cityId) {
		return new ZoneDefinition(id, cityId, id,
			List.of(cityId + "10100"), new BigDecimal("3.0"));
	}
}
