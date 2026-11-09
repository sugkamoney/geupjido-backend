package com.geupjido.match.api;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.condition.EnabledIfEnvironmentVariable;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.web.servlet.MockMvc;

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
class WorldcupZoneSelectionApiIT {

	@Autowired private MockMvc mockMvc;
	@Autowired private JdbcTemplate jdbcTemplate;
	@Autowired private ObjectMapper objectMapper;

	@Test
	void 권역_목록과_대체_권역_조회는_게임을_만들지_않는다() throws Exception {
		int gamesBefore = jdbcTemplate.queryForObject(
			"SELECT COUNT(*) FROM worldcup_game", Integer.class);
		String zoneId = jdbcTemplate.queryForObject(
			"SELECT id FROM zone ORDER BY id LIMIT 1", String.class);

		var response = mockMvc.perform(get("/api/v1/worldcup/zones"))
			.andExpect(status().isOk())
			.andExpect(header().string("Cache-Control", "no-store"))
			.andExpect(jsonPath("$[0].zoneId").isNotEmpty())
			.andExpect(jsonPath("$[0].cityName").isNotEmpty())
			.andExpect(jsonPath("$[0].activeComplexCount").isNumber())
			.andExpect(jsonPath("$[0].canStart").isBoolean())
			.andReturn().getResponse().getContentAsString();
		var zones = objectMapper.readTree(response);
		assertThat(zones.size()).isEqualTo(jdbcTemplate.queryForObject(
			"SELECT COUNT(*) FROM zone", Integer.class));
		for (var zone : zones) {
			if (zone.get("zoneId").asText().equals(zoneId)) {
				assertThat(zone.get("activeComplexCount").asLong()).isEqualTo(
					jdbcTemplate.queryForObject(
						"SELECT COUNT(*) FROM complex WHERE zone_id = ? AND is_active = TRUE",
						Long.class, zoneId));
			}
		}
		mockMvc.perform(get("/api/v1/worldcup/zones/{zoneId}/alternatives", zoneId))
			.andExpect(status().isOk())
			.andExpect(header().string("Cache-Control", "no-store"));
		assertThat(jdbcTemplate.queryForObject(
			"SELECT COUNT(*) FROM worldcup_game", Integer.class)).isEqualTo(gamesBefore);
	}

	@Test
	void 없는_권역의_대체_목록은_404다() throws Exception {
		mockMvc.perform(get("/api/v1/worldcup/zones/{zoneId}/alternatives", "unknown-zone"))
			.andExpect(status().isNotFound())
			.andExpect(jsonPath("$.code").value("ZONE_NOT_FOUND"));
	}
}
