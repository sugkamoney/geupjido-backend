package com.geupjido.map.api;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.condition.EnabledIfEnvironmentVariable;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.transaction.annotation.Transactional;

import com.fasterxml.jackson.databind.ObjectMapper;

import static org.assertj.core.api.Assertions.assertThat;

import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.header;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

@EnabledIfEnvironmentVariable(named = "RUN_POSTGIS_IT", matches = "true")
@SpringBootTest
@AutoConfigureMockMvc
@ActiveProfiles("local")
@Transactional
class MapApiIT {

	@Autowired private MockMvc mockMvc;
	@Autowired private JdbcTemplate jdbcTemplate;
	@Autowired private ObjectMapper objectMapper;

	@Test
	void 실제_권역과_단지_좌표를_지도_응답으로_조회한다() throws Exception {
		String zoneId = jdbcTemplate.queryForObject(
			"SELECT id FROM zone ORDER BY id LIMIT 1", String.class);
		String zoneName = jdbcTemplate.queryForObject(
			"SELECT name FROM zone WHERE id = ?", String.class, zoneId);

		mockMvc.perform(get("/api/v1/map/cities"))
			.andExpect(status().isOk())
			.andExpect(jsonPath("$[0].center.longitude").isNumber())
			.andExpect(jsonPath("$[0].bounds.west").isNumber());
		mockMvc.perform(get("/api/v1/map/zones")
				.param("west", "124").param("south", "33")
				.param("east", "132").param("north", "39"))
			.andExpect(status().isOk())
			.andExpect(jsonPath("$[0].initialTier").isNumber());
		mockMvc.perform(get("/api/v1/map/complexes")
				.param("west", "124").param("south", "33")
				.param("east", "132").param("north", "39"))
			.andExpect(status().isOk())
			.andExpect(jsonPath("$[0].location.latitude").isNumber());
		mockMvc.perform(get("/api/v1/map/zones/{zoneId}", zoneId))
			.andExpect(status().isOk())
			.andExpect(jsonPath("$.zone.name").value(zoneName))
			.andExpect(jsonPath("$.polygon.type").value("MultiPolygon"))
			.andExpect(jsonPath("$.complexes").isArray());
	}

	@Test
	void 투표_비활성_단지도_지도_상세에는_포함한다() throws Exception {
		String zoneId = jdbcTemplate.queryForObject("""
			SELECT zone_id FROM complex
			GROUP BY zone_id
			HAVING count(*) FILTER (WHERE is_active = FALSE) > 0
			ORDER BY count(*) DESC LIMIT 1
			""", String.class);
		Long expected = jdbcTemplate.queryForObject(
			"SELECT count(*) FROM complex WHERE zone_id = ?", Long.class, zoneId);
		String body = mockMvc.perform(get("/api/v1/map/zones/{zoneId}", zoneId))
			.andExpect(status().isOk())
			.andReturn().getResponse().getContentAsString();
		assertThat(objectMapper.readTree(body).path("complexes").size())
			.isEqualTo(expected.intValue());
	}

	@Test
	void 거래가_없는_단지도_지도_상세에_가격없이_표시한다() throws Exception {
		String complexId = jdbcTemplate.queryForObject(
			"SELECT id FROM complex WHERE zone_id IS NOT NULL ORDER BY id LIMIT 1",
			String.class);
		String zoneId = jdbcTemplate.queryForObject(
			"SELECT zone_id FROM complex WHERE id = ?", String.class, complexId);
		jdbcTemplate.update("""
			UPDATE complex
			SET price = NULL, price_source = NULL,
			    representative_area = NULL, last_deal_date = NULL
			WHERE id = ?
			""", complexId);

		String body = mockMvc.perform(get("/api/v1/map/zones/{zoneId}", zoneId))
			.andExpect(status().isOk())
			.andReturn().getResponse().getContentAsString();
		var complexes = objectMapper.readTree(body).path("complexes");
		var complex = java.util.stream.StreamSupport.stream(complexes.spliterator(), false)
			.filter(item -> complexId.equals(item.path("id").asText()))
			.findFirst().orElseThrow();
		assertThat(complex.path("price").isNull()).isTrue();
		assertThat(complex.path("representativeArea").isNull()).isTrue();
		assertThat(complex.path("lastDealDate").isNull()).isTrue();
	}

	@Test
	void 투표_전환_전후의_권역_급지와_순위_기준을_구별한다() throws Exception {
		String zoneId = jdbcTemplate.queryForObject(
			"SELECT id FROM zone ORDER BY id LIMIT 1", String.class);
		jdbcTemplate.update("UPDATE zone SET ranking_basis = 'INITIAL' WHERE id = ?", zoneId);
		String before = mockMvc.perform(get("/api/v1/map/zones/{zoneId}", zoneId))
			.andExpect(status().isOk())
			.andExpect(jsonPath("$.zone.rankingBasis").value("INITIAL"))
			.andReturn().getResponse().getContentAsString();
		assertThat(objectMapper.readTree(before).path("zone").path("voteTier").isNull())
			.isTrue();

		jdbcTemplate.update("UPDATE zone SET ranking_basis = 'VOTE' WHERE id = ?", zoneId);
		String after = mockMvc.perform(get("/api/v1/map/zones/{zoneId}", zoneId))
			.andExpect(status().isOk())
			.andExpect(jsonPath("$.zone.rankingBasis").value("VOTE"))
			.andReturn().getResponse().getContentAsString();
		assertThat(objectMapper.readTree(after).path("zone").path("voteTier").isNumber())
			.isTrue();
	}

	@Test
	void 검색과_상태_및_오류_응답을_조회한다() throws Exception {
		mockMvc.perform(get("/api/v1/map/search").param("q", "압구정"))
			.andExpect(status().isOk())
			.andExpect(jsonPath("$.zones[0].name").value("압구정"))
			.andExpect(jsonPath("$.complexes[0].cityName").isString());
		mockMvc.perform(get("/api/v1/map/search").param("q", "없는권역가나다"))
			.andExpect(status().isOk())
			.andExpect(jsonPath("$.zones").isEmpty());
		mockMvc.perform(get("/api/v1/map/status"))
			.andExpect(status().isOk())
			.andExpect(jsonPath("$.zoneCount").isNumber())
			.andExpect(jsonPath("$.validVoteCount").isNumber());
		mockMvc.perform(get("/api/v1/map/zones")
				.param("west", "128").param("south", "33")
				.param("east", "127").param("north", "39"))
			.andExpect(status().isBadRequest())
			.andExpect(jsonPath("$.code").value("INVALID_REQUEST"));
		mockMvc.perform(get("/api/v1/map/zones/unknown-zone"))
			.andExpect(status().isNotFound())
			.andExpect(jsonPath("$.code").value("ZONE_NOT_FOUND"));
		mockMvc.perform(get("/api/v1/map/status")
				.header("Origin", "http://localhost:5173"))
			.andExpect(status().isOk())
			.andExpect(header().string("Access-Control-Allow-Origin", "http://localhost:5173"));
	}
}
