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
			.andExpect(jsonPath("$[0].bounds.west").isNumber())
			.andExpect(jsonPath("$[0].initialTierZoneCount").isNumber())
			.andExpect(jsonPath("$[0].voteTierZoneCount").isNumber());
		mockMvc.perform(get("/api/v1/map/zones")
				.param("west", "124").param("south", "33")
				.param("east", "132").param("north", "39"))
			.andExpect(status().isOk())
			.andExpect(jsonPath("$[0].initialTier").isNumber())
			.andExpect(jsonPath("$[0].initialRank").isNumber())
			.andExpect(jsonPath("$[0].initialRankTotal").isNumber());
		mockMvc.perform(get("/api/v1/map/complexes")
				.param("west", "124").param("south", "33")
				.param("east", "132").param("north", "39"))
			.andExpect(status().isOk())
			.andExpect(jsonPath("$[0].location.latitude").isNumber());
		mockMvc.perform(get("/api/v1/map/zones/{zoneId}", zoneId))
			.andExpect(status().isOk())
			.andExpect(jsonPath("$.zone.name").value(zoneName))
			.andExpect(jsonPath("$.zone.regionName").isString())
			.andExpect(jsonPath("$.polygon.type").value("MultiPolygon"))
			.andExpect(jsonPath("$.complexes").isArray());
	}

	@Test
	void 권역_면적은_목록_검색_상세에서_같고_실제_경계를_반영한다() throws Exception {
		String zoneId = jdbcTemplate.queryForObject(
			"SELECT id FROM zone ORDER BY id LIMIT 1", String.class);
		String zoneName = jdbcTemplate.queryForObject(
			"SELECT name FROM zone WHERE id = ?", String.class, zoneId);
		String polygon = "MULTIPOLYGON(((127 37,127.04 37,127.04 37.04,127 37.04,127 37),"
			+ "(127.01 37.01,127.01 37.02,127.02 37.02,127.02 37.01,127.01 37.01)),"
			+ "((127.05 37,127.06 37,127.06 37.01,127.05 37.01,127.05 37)))";
		jdbcTemplate.update("UPDATE zone SET polygon = ST_GeomFromText(?, 4326) WHERE id = ?",
			polygon, zoneId);
		Double expected = jdbcTemplate.queryForObject("""
			SELECT ST_Area(ST_GeomFromText(
			         'POLYGON((127 37,127.04 37,127.04 37.04,127 37.04,127 37))',
			         4326)::geography)
			     - ST_Area(ST_GeomFromText(
			         'POLYGON((127.01 37.01,127.01 37.02,127.02 37.02,127.02 37.01,127.01 37.01))',
			         4326)::geography)
			     + ST_Area(ST_GeomFromText(
			         'POLYGON((127.05 37,127.06 37,127.06 37.01,127.05 37.01,127.05 37))',
			         4326)::geography)
			""", Double.class);

		var list = objectMapper.readTree(mockMvc.perform(get("/api/v1/map/zones")
				.param("west", "126").param("south", "36")
				.param("east", "128").param("north", "38"))
			.andExpect(status().isOk()).andReturn().getResponse().getContentAsString());
		var listZone = java.util.stream.StreamSupport.stream(list.spliterator(), false)
			.filter(zone -> zoneId.equals(zone.path("id").asText())).findFirst().orElseThrow();
		var search = objectMapper.readTree(mockMvc.perform(get("/api/v1/map/search")
				.param("q", zoneName)).andExpect(status().isOk())
			.andReturn().getResponse().getContentAsString());
		var searchZone = java.util.stream.StreamSupport
			.stream(search.path("zones").spliterator(), false)
			.filter(zone -> zoneId.equals(zone.path("id").asText())).findFirst().orElseThrow();
		var detail = objectMapper.readTree(mockMvc.perform(get("/api/v1/map/zones/{zoneId}", zoneId))
			.andExpect(status().isOk()).andReturn().getResponse().getContentAsString());
		assertThat(listZone.path("areaSquareMeters").asDouble()).isPositive()
			.isCloseTo(expected, org.assertj.core.data.Offset.offset(0.01));
		assertThat(searchZone.path("areaSquareMeters").asDouble())
			.isEqualTo(listZone.path("areaSquareMeters").asDouble());
		assertThat(detail.path("zone").path("areaSquareMeters").asDouble())
			.isEqualTo(listZone.path("areaSquareMeters").asDouble());
		assertThat(detail.path("polygon").path("type").asText()).isEqualTo("MultiPolygon");
		assertThat(detail.path("polygon").path("coordinates").size()).isEqualTo(2);
		assertThat(detail.path("polygon").path("coordinates").get(0).size()).isEqualTo(2);
		Long nonPositiveAreaCount = jdbcTemplate.queryForObject("""
			SELECT count(*) FROM zone z
			JOIN city c ON c.id = z.city_id
			JOIN region r ON r.code = c.region_code AND r.is_active = TRUE
			WHERE ST_Area(z.polygon::geography) <= 0
			""", Long.class);
		assertThat(nonPositiveAreaCount).isZero();
	}

	@Test
	void 상세_단지_좌표는_핀과_검색에서_일치하고_위치_누락은_null이다() throws Exception {
		var sample = jdbcTemplate.queryForMap("""
			SELECT x.id, x.zone_id, x.name, ST_X(x.location) AS longitude,
			       ST_Y(x.location) AS latitude
			FROM complex x
			WHERE x.zone_id IS NOT NULL AND x.location IS NOT NULL
			  AND (SELECT count(*) FROM complex same_name WHERE same_name.name = x.name) = 1
			ORDER BY x.id LIMIT 1
			""");
		String complexId = (String) sample.get("id");
		String zoneId = (String) sample.get("zone_id");
		String name = (String) sample.get("name");
		double longitude = ((Number) sample.get("longitude")).doubleValue();
		double latitude = ((Number) sample.get("latitude")).doubleValue();

		var detail = objectMapper.readTree(mockMvc.perform(get("/api/v1/map/zones/{zoneId}", zoneId))
			.andExpect(status().isOk()).andReturn().getResponse().getContentAsString());
		var complex = java.util.stream.StreamSupport
			.stream(detail.path("complexes").spliterator(), false)
			.filter(item -> complexId.equals(item.path("id").asText())).findFirst().orElseThrow();
		var originalOrder = java.util.stream.StreamSupport
			.stream(detail.path("complexes").spliterator(), false)
			.map(item -> item.path("id").asText()).toList();
		assertThat(complex.path("location").path("longitude").asDouble()).isEqualTo(longitude);
		assertThat(complex.path("location").path("latitude").asDouble()).isEqualTo(latitude);

		var pins = objectMapper.readTree(mockMvc.perform(get("/api/v1/map/complexes")
				.param("west", Double.toString(longitude - 0.001))
				.param("south", Double.toString(latitude - 0.001))
				.param("east", Double.toString(longitude + 0.001))
				.param("north", Double.toString(latitude + 0.001)))
			.andExpect(status().isOk()).andReturn().getResponse().getContentAsString());
		var pin = java.util.stream.StreamSupport.stream(pins.spliterator(), false)
			.filter(item -> complexId.equals(item.path("id").asText())).findFirst().orElseThrow();
		var search = objectMapper.readTree(mockMvc.perform(get("/api/v1/map/search")
				.param("q", name)).andExpect(status().isOk())
			.andReturn().getResponse().getContentAsString());
		var searchPin = java.util.stream.StreamSupport
			.stream(search.path("complexes").spliterator(), false)
			.filter(item -> complexId.equals(item.path("id").asText())).findFirst().orElseThrow();
		assertThat(pin.path("location")).isEqualTo(complex.path("location"));
		assertThat(searchPin.path("location")).isEqualTo(complex.path("location"));

		jdbcTemplate.update("UPDATE complex SET location = NULL WHERE id = ?", complexId);
		var withoutLocation = objectMapper.readTree(mockMvc.perform(
			get("/api/v1/map/zones/{zoneId}", zoneId))
			.andExpect(status().isOk()).andReturn().getResponse().getContentAsString());
		var sameComplex = java.util.stream.StreamSupport
			.stream(withoutLocation.path("complexes").spliterator(), false)
			.filter(item -> complexId.equals(item.path("id").asText())).findFirst().orElseThrow();
		var orderWithoutLocation = java.util.stream.StreamSupport
			.stream(withoutLocation.path("complexes").spliterator(), false)
			.map(item -> item.path("id").asText()).toList();
		assertThat(sameComplex.path("location").isNull()).isTrue();
		assertThat(sameComplex.path("zoneRank")).isEqualTo(complex.path("zoneRank"));
		assertThat(orderWithoutLocation).containsExactlyElementsOf(originalOrder);
	}

	@Test
	void 현재_서비스_단지는_모두_좌표가_있다() {
		Long missingLocationCount = jdbcTemplate.queryForObject("""
			SELECT count(*) FROM complex x
			JOIN zone z ON z.id = x.zone_id
			JOIN city c ON c.id = z.city_id
			JOIN region r ON r.code = c.region_code AND r.is_active = TRUE
			WHERE x.location IS NULL
			""", Long.class);
		assertThat(missingLocationCount).isZero();
	}

	@Test
	void 지도_OpenAPI는_실제_응답과_오류_계약을_설명한다() throws Exception {
		var docs = objectMapper.readTree(mockMvc.perform(get("/v3/api-docs"))
			.andExpect(status().isOk()).andReturn().getResponse().getContentAsString());
		var schemas = docs.path("components").path("schemas");
		assertThat(schemas.path("MapZone").path("properties").path("areaSquareMeters").isObject())
			.isTrue();
		assertThat(schemas.path("RankingZone").path("properties").path("rank").isObject())
			.isTrue();
		assertThat(docs.path("paths").path("/api/v1/map/zones").path("get")
			.path("responses").path("200").path("content").path("application/json")
			.path("schema").path("items").path("$ref").asText())
			.isEqualTo("#/components/schemas/MapZone");
		assertThat(docs.path("paths").path("/api/v1/rankings/zones").path("get")
			.path("responses").path("200").path("content").path("application/json")
			.path("schema").path("$ref").asText())
			.isEqualTo("#/components/schemas/Response");
		assertThat(schemas.path("Response").path("properties").path("zones")
			.path("items").path("$ref").asText())
			.isEqualTo("#/components/schemas/RankingZone");
		assertThat(schemas.path("MapZone").path("properties").path("center").isObject())
			.isTrue();
		var areaMinimum = schemas.path("MapZone").path("properties")
			.path("areaSquareMeters").path("minimum");
		assertThat(areaMinimum.isNumber() && areaMinimum.asDouble() == 0.0).isTrue();
		assertThat(schemas.path("MapZone").path("required").toString())
			.contains("areaSquareMeters", "voteTier", "initialRank");
		assertThat(schemas.path("MapZone").path("properties").path("voteTier")
			.path("type").toString()).contains("null");
		assertThat(schemas.path("MapComplex").path("properties").path("price")
			.path("type").toString()).contains("null");
		assertThat(schemas.path("MapComplex").path("properties").path("location").isObject())
			.isTrue();
		var location = schemas.path("MapComplex").path("properties").path("location");
		assertThat(location.path("oneOf").get(0).path("$ref").asText())
			.isEqualTo("#/components/schemas/Point");
		assertThat(location.path("oneOf").get(1).path("type").toString())
			.contains("null");
		assertThat(schemas.path("ZoneDetailResponse").path("properties").path("polygon")
			.path("$ref").asText()).isEqualTo("#/components/schemas/GeoJsonMultiPolygon");
		assertThat(schemas.path("GeoJsonMultiPolygon").path("properties")
			.path("coordinates").path("items").path("items").path("items")
			.path("items").path("type").asText()).isEqualTo("number");
		assertThat(docs.path("paths").path("/api/v1/map/search").path("get")
			.path("responses").path("400").isObject()).isTrue();
		assertThat(docs.path("paths").path("/api/v1/map/zones/{zoneId}").path("get")
			.path("responses").path("404").isObject()).isTrue();
		assertThat(docs.path("paths").path("/api/v1/map/search").path("get")
			.path("parameters").get(0).path("schema").path("maxLength").asInt()).isEqualTo(80);
		assertThat(docs.path("paths").path("/api/v1/map/search").path("get")
			.path("parameters").get(0).path("schema").path("minLength").asInt()).isEqualTo(1);
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
		assertThat(objectMapper.readTree(before).path("zone").path("voteRank").isNull())
			.isTrue();

		jdbcTemplate.update("UPDATE zone SET ranking_basis = 'VOTE' WHERE id = ?", zoneId);
		String after = mockMvc.perform(get("/api/v1/map/zones/{zoneId}", zoneId))
			.andExpect(status().isOk())
			.andExpect(jsonPath("$.zone.rankingBasis").value("VOTE"))
			.andReturn().getResponse().getContentAsString();
		assertThat(objectMapper.readTree(after).path("zone").path("voteTier").isNumber())
			.isTrue();
		assertThat(objectMapper.readTree(after).path("zone").path("voteRank").isNumber())
			.isTrue();
		String statusBody = mockMvc.perform(get("/api/v1/map/status"))
			.andExpect(status().isOk())
			.andReturn().getResponse().getContentAsString();
		var publicationStatus = objectMapper.readTree(statusBody);
		assertThat(publicationStatus.path("voteBasedZoneCount").asLong()
			+ publicationStatus.path("collectingZoneCount").asLong())
			.isEqualTo(publicationStatus.path("zoneCount").asLong());
	}

	@Test
	void 초기_급지가_없는_권역은_순위를_비우고_상태_집계에_반영한다() throws Exception {
		String zoneId = jdbcTemplate.queryForObject(
			"SELECT id FROM zone ORDER BY id LIMIT 1", String.class);
		jdbcTemplate.update("UPDATE zone SET initial_tier = NULL WHERE id = ?", zoneId);

		String detail = mockMvc.perform(get("/api/v1/map/zones/{zoneId}", zoneId))
			.andExpect(status().isOk())
			.andReturn().getResponse().getContentAsString();
		assertThat(objectMapper.readTree(detail).path("zone").path("initialRank").isNull())
			.isTrue();

		String statusBody = mockMvc.perform(get("/api/v1/map/status"))
			.andExpect(status().isOk())
			.andReturn().getResponse().getContentAsString();
		var status = objectMapper.readTree(statusBody);
		assertThat(status.path("missingInitialTierZoneCount").asLong()).isEqualTo(1);
		assertThat(status.path("initialTierZoneCount").asLong()
			+ status.path("missingInitialTierZoneCount").asLong())
			.isEqualTo(status.path("zoneCount").asLong());
		long bandTotal = 0;
		for (var band : status.path("initialGradeCounts")) {
			bandTotal += band.path("count").asLong();
		}
		assertThat(bandTotal).isEqualTo(status.path("initialTierZoneCount").asLong());
	}

	@Test
	void 지도_전체_권역의_초기_순위는_중복_없이_매겨지고_구_집계와_일치한다() throws Exception {
		String zoneBody = mockMvc.perform(get("/api/v1/map/zones")
				.param("west", "124").param("south", "33")
				.param("east", "132").param("north", "39"))
			.andExpect(status().isOk())
			.andReturn().getResponse().getContentAsString();
		var zones = objectMapper.readTree(zoneBody);
		var initialRanks = new java.util.HashSet<Long>();
		for (var zone : zones) {
			if (!zone.path("initialRank").isNull()) {
				assertThat(initialRanks.add(zone.path("initialRank").asLong())).isTrue();
			}
			assertThat(zone.path("initialRankTotal").asLong())
				.isEqualTo(zones.get(0).path("initialRankTotal").asLong());
		}
		assertThat(initialRanks).hasSize(zones.get(0).path("initialRankTotal").asInt());

		String cityBody = mockMvc.perform(get("/api/v1/map/cities"))
			.andExpect(status().isOk())
			.andReturn().getResponse().getContentAsString();
		long cityInitialTotal = 0;
		for (var city : objectMapper.readTree(cityBody)) {
			cityInitialTotal += city.path("initialTierZoneCount").asLong();
		}
		assertThat(cityInitialTotal).isEqualTo(initialRanks.size());
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
		mockMvc.perform(get("/api/v1/map/search").param("q", "가".repeat(81)))
			.andExpect(status().isBadRequest())
			.andExpect(jsonPath("$.code").value("INVALID_REQUEST"));
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
