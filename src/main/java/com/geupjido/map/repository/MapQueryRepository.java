package com.geupjido.map.repository;

import java.sql.ResultSet;
import java.sql.SQLException;
import java.time.OffsetDateTime;
import java.util.List;
import java.util.Optional;

import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Repository;

import com.geupjido.map.model.MapView;
import com.geupjido.zone.domain.RankingBasis;

/** 지도 조회 전용 SQL. 기존 JPA 엔티티를 수정하지 않고 필요한 컬럼만 읽는다. */
@Repository
public class MapQueryRepository {

	// 권역 목록·검색·상세가 같은 급지 해석을 쓰도록 공통 SELECT를 재사용한다.
	// vote_tier는 전환 전 INITIAL 권역에서 null이 되어 '모으는 중' 표시가 가능하다.
	private static final String ZONES = """
		SELECT z.id, z.city_id, c.name AS city_name, c.region_code,
		       z.name, ST_X(z.center) AS longitude, ST_Y(z.center) AS latitude,
		       z.initial_tier, CASE WHEN z.ranking_basis = 'VOTE' THEN z.tier END AS vote_tier,
		       z.ranking_basis,
		       (SELECT count(*) FROM complex x WHERE x.zone_id = z.id) AS complex_count,
		       COALESCE(s.valid_vote_count, 0) AS valid_vote_count
		FROM zone z
		JOIN city c ON c.id = z.city_id
		JOIN region r ON r.code = c.region_code AND r.is_active = TRUE
		LEFT JOIN worldcup_zone_vote_status s ON s.zone_id = z.id
		""";

	private final JdbcTemplate jdbcTemplate;

	public MapQueryRepository(JdbcTemplate jdbcTemplate) {
		this.jdbcTemplate = jdbcTemplate;
	}

	public List<MapView.City> cities() {
		// 구 평균은 각 권역 급지의 평균을 소수 첫째 자리로 내린다.
		// 민심 평균에는 이미 VOTE로 전환한 권역만 넣는다.
		return jdbcTemplate.query("""
			SELECT c.id, c.region_code, r.name AS region_name, c.name,
			       ST_X(c.center) AS longitude, ST_Y(c.center) AS latitude,
			       ST_XMin(c.polygon) AS west, ST_YMin(c.polygon) AS south,
			       ST_XMax(c.polygon) AS east, ST_YMax(c.polygon) AS north,
			       count(z.id) AS zone_count,
			       floor(avg(z.initial_tier) * 10) / 10 AS initial_tier,
			       floor(avg(z.tier) FILTER (WHERE z.ranking_basis = 'VOTE') * 10) / 10
			           AS vote_tier
			FROM city c
			JOIN region r ON r.code = c.region_code AND r.is_active = TRUE
			LEFT JOIN zone z ON z.city_id = c.id
			GROUP BY c.id, c.region_code, r.name, c.name, c.center, r.display_order
			ORDER BY r.display_order, c.name, c.id
			""", (rs, rowNum) -> new MapView.City(
			rs.getString("id"), rs.getString("region_code"),
			rs.getString("region_name"), rs.getString("name"), point(rs),
			new MapView.Bounds(rs.getDouble("west"), rs.getDouble("south"),
				rs.getDouble("east"), rs.getDouble("north")),
			rs.getLong("zone_count"), rs.getBigDecimal("initial_tier"),
			rs.getBigDecimal("vote_tier")
		));
	}

	public List<MapView.Zone> zones(MapView.Bounds bounds) {
		// polygon과 화면 사각형이 겹치는 권역만 찾는다. GeoJSON 전체는 보내지
		// 않고 중심 좌표만 보내므로 지도 이동 때 응답 크기를 줄일 수 있다.
		return jdbcTemplate.query(ZONES + """
			WHERE ST_Intersects(z.polygon, ST_MakeEnvelope(?, ?, ?, ?, 4326))
			ORDER BY c.name, z.name, z.id
			""", (rs, rowNum) -> zone(rs),
			bounds.west(), bounds.south(), bounds.east(), bounds.north());
	}

	public List<MapView.Zone> allZones() {
		return jdbcTemplate.query(ZONES + "ORDER BY c.name, z.name, z.id",
			(rs, rowNum) -> zone(rs));
	}

	public List<MapView.ComplexPin> pins(MapView.Bounds bounds) {
		// PostGIS의 &&는 두 도형의 경계 상자가 겹치는지 확인하며 GiST 인덱스를
		// 사용할 수 있다. 포인트인 단지 위치에서는 화면 안 점 판별과 같다.
		return jdbcTemplate.query("""
			SELECT x.id, x.zone_id, c.name AS city_name,
			       z.name AS zone_name, x.name, x.households,
			       ST_X(x.location) AS longitude, ST_Y(x.location) AS latitude
			FROM complex x
			JOIN zone z ON z.id = x.zone_id
			JOIN city c ON c.id = z.city_id
			JOIN region r ON r.code = c.region_code AND r.is_active = TRUE
			WHERE x.location && ST_MakeEnvelope(?, ?, ?, ?, 4326)
			ORDER BY x.households DESC, x.id
			""", (rs, rowNum) -> pin(rs),
			bounds.west(), bounds.south(), bounds.east(), bounds.north());
	}

	public List<MapView.ComplexPin> searchComplexes(String normalizedQuery) {
		return jdbcTemplate.query("""
			SELECT x.id, x.zone_id, c.name AS city_name,
			       z.name AS zone_name, x.name, x.households,
			       ST_X(x.location) AS longitude, ST_Y(x.location) AS latitude
			FROM complex x
			JOIN zone z ON z.id = x.zone_id
			JOIN city c ON c.id = z.city_id
			JOIN region r ON r.code = c.region_code AND r.is_active = TRUE
			WHERE x.location IS NOT NULL
			  AND POSITION(? IN replace(lower(x.name), ' ', '')) > 0
			ORDER BY POSITION(? IN replace(lower(x.name), ' ', '')),
			         length(x.name), x.name, x.id
			LIMIT 6
			""", (rs, rowNum) -> pin(rs), normalizedQuery, normalizedQuery);
	}

	public Optional<MapView.ZoneDetail> zoneDetail(String zoneId) {
		List<MapView.Zone> zones = jdbcTemplate.query(ZONES + "WHERE z.id = ?",
			(rs, rowNum) -> zone(rs), zoneId);
		if (zones.isEmpty()) {
			return Optional.empty();
		}
		// 경계 도형은 선택한 권역 한 곳에서만 GeoJSON으로 변환한다.
		String polygon = jdbcTemplate.queryForObject(
			"SELECT ST_AsGeoJSON(polygon, 6) FROM zone WHERE id = ?",
			String.class, zoneId);
		return Optional.of(new MapView.ZoneDetail(zones.getFirst(), polygon,
			complexes(zoneId)));
	}

	private List<MapView.Complex> complexes(String zoneId) {
		// 지도 상세에는 비활성 단지도 포함한다. 다만 투표로 순위가 바뀐 권역에서는
		// 투표 후보가 아닌 단지의 오래된 실거래 잠정 순위를 민심 순위로 오해하지
		// 않도록 응답 순위·점수를 null로 바꾼다.
		return jdbcTemplate.query("""
			SELECT x.id, x.name,
			       CASE WHEN z.ranking_basis = 'VOTE' AND x.is_active = FALSE
			            THEN NULL ELSE x.zone_rank END AS display_rank,
			       CASE WHEN z.ranking_basis = 'VOTE' AND x.is_active = FALSE
			            THEN NULL ELSE x.score END AS display_score,
			       x.price, x.representative_area, x.last_deal_date,
			       x.approval_date, x.households, x.is_active
			FROM complex x JOIN zone z ON z.id = x.zone_id
			WHERE x.zone_id = ?
			ORDER BY display_rank NULLS LAST, x.price DESC NULLS LAST, x.id
			""", (rs, rowNum) -> new MapView.Complex(
			rs.getString("id"), rs.getString("name"),
			rs.getObject("display_rank", Integer.class),
			rs.getBigDecimal("display_score"),
			rs.getObject("price", Integer.class),
			rs.getBigDecimal("representative_area"),
			rs.getObject("last_deal_date", java.time.LocalDate.class),
			rs.getObject("approval_date", java.time.LocalDate.class),
			rs.getInt("households"), rs.getBoolean("is_active")
		), zoneId);
	}

	public MapView.Status status() {
		// 투표 원본 전체를 매 요청마다 다시 집계하지 않고 마지막 발행 행을 읽는다.
		// 예약 시각 계산은 애플리케이션 설정을 아는 Service가 맡는다.
		return jdbcTemplate.queryForObject("""
			SELECT count(z.id) AS zone_count,
			       count(z.id) FILTER (WHERE z.ranking_basis = 'VOTE') AS vote_zone_count,
			       p.valid_vote_count, p.participant_count, p.last_completed_at
			FROM worldcup_rating_publication p
			CROSS JOIN zone z
			JOIN city c ON c.id = z.city_id
			JOIN region r ON r.code = c.region_code AND r.is_active = TRUE
			WHERE p.id = 1
			GROUP BY p.valid_vote_count, p.participant_count, p.last_completed_at
			""", (rs, rowNum) -> {
			OffsetDateTime completedAt = rs.getObject("last_completed_at",
				OffsetDateTime.class);
			return new MapView.Status(rs.getLong("zone_count"),
				rs.getLong("vote_zone_count"), rs.getLong("valid_vote_count"),
				rs.getLong("participant_count"),
				completedAt == null ? null : completedAt.toInstant(), null);
		});
	}

	private static MapView.Zone zone(ResultSet rs) throws SQLException {
		return new MapView.Zone(rs.getString("id"), rs.getString("city_id"),
			rs.getString("city_name"), rs.getString("region_code"),
			rs.getString("name"), point(rs), rs.getBigDecimal("initial_tier"),
			rs.getBigDecimal("vote_tier"),
			RankingBasis.valueOf(rs.getString("ranking_basis")),
			rs.getLong("complex_count"), rs.getInt("valid_vote_count"));
	}

	private static MapView.ComplexPin pin(ResultSet rs) throws SQLException {
		return new MapView.ComplexPin(rs.getString("id"),
			rs.getString("zone_id"), rs.getString("city_name"),
			rs.getString("zone_name"), rs.getString("name"), point(rs),
			rs.getInt("households"));
	}

	private static MapView.Point point(ResultSet rs) throws SQLException {
		return new MapView.Point(rs.getDouble("longitude"), rs.getDouble("latitude"));
	}
}
