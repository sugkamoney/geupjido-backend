package com.geupjido.map.service;

import java.time.Clock;
import java.time.Instant;
import java.time.ZoneId;
import java.util.Comparator;
import java.util.List;
import java.util.Locale;

import org.springframework.beans.factory.annotation.Value;
import org.springframework.scheduling.support.CronExpression;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import com.geupjido.map.model.MapView;
import com.geupjido.map.repository.MapQueryRepository;

/**
 * 지도 탭의 조회 규칙을 조립한다. @Transactional(readOnly = true)는 여러 조회가
 * 영속성 데이터를 바꾸지 않는다는 의도를 나타낸다.
 */
@Service
@Transactional(readOnly = true)
public class MapQueryService {

	private static final ZoneId SEOUL = ZoneId.of("Asia/Seoul");
	private final MapQueryRepository repository;
	private final Clock clock;
	private final boolean scheduleEnabled;
	private final CronExpression schedule;

	public MapQueryService(MapQueryRepository repository, Clock clock,
		@Value("${app.worldcup-rating.schedule-enabled:false}") boolean scheduleEnabled,
		@Value("${app.worldcup-rating.schedule-cron:0 0 */3 * * *}") String cron
	) {
		this.repository = repository;
		this.clock = clock;
		this.scheduleEnabled = scheduleEnabled;
		this.schedule = CronExpression.parse(cron);
	}

	public List<MapView.City> cities() {
		return repository.cities();
	}

	public List<MapView.Zone> zones(MapView.Bounds bounds) {
		return repository.zones(bounds);
	}

	public List<MapView.ComplexPin> pins(MapView.Bounds bounds) {
		return repository.pins(bounds);
	}

	public MapView.ZoneDetail zone(String zoneId) {
		// Optional.empty()를 HTTP 404로 바꿀 수 있게 도메인 예외를 던진다.
		return repository.zoneDetail(zoneId)
			.orElseThrow(() -> new MapZoneNotFoundException(zoneId));
	}

	public MapView.SearchResult search(String query) {
		if (query == null || query.isBlank() || query.length() > 80) {
			throw new IllegalArgumentException("검색어는 1~80자로 입력해 주세요.");
		}
		String term = normalize(query);
		// 검색어가 이름의 앞부분에 있을수록 우선하고, 같으면 짧은 이름을 앞에 둔다.
		Comparator<String> order = Comparator.comparingInt(
			(String name) -> normalize(name).indexOf(term))
			.thenComparingInt(String::length).thenComparing(String::compareTo);
		List<MapView.City> cities = repository.cities().stream()
			.filter(city -> normalize(city.name()).contains(term)
				|| normalize(city.name().replaceFirst("[구시]$", "")).contains(term))
			.sorted(Comparator.comparing(MapView.City::name, order))
			.limit(3).toList();
		Comparator<MapView.Zone> zoneOrder = Comparator
			.comparingInt((MapView.Zone zone) -> Math.min(
				matchPosition(normalize(zone.name()), term),
				matchPosition(normalize(zone.cityName() + zone.name()), term)))
			.thenComparingInt(zone -> zone.name().length())
			.thenComparing(MapView.Zone::name)
			.thenComparing(MapView.Zone::id);
		List<MapView.Zone> zones = repository.allZones().stream()
			.filter(zone -> normalize(zone.name()).contains(term)
				|| normalize(zone.cityName() + zone.name()).contains(term))
			.sorted(zoneOrder)
			.limit(5).toList();
		return new MapView.SearchResult(cities, zones,
			repository.searchComplexes(term));
	}

	public MapView.Status status() {
		MapView.Status current = repository.status();
		Instant next = null;
		if (scheduleEnabled) {
			// cron 식은 한국 시간을 기준으로 해석한다. 로컬처럼 예약 실행이 꺼져
			// 있으면 '다음 반영' 시각을 임의로 약속하지 않고 null로 준다.
			var nextLocal = schedule.next(clock.instant().atZone(SEOUL).toLocalDateTime());
			if (nextLocal != null) {
				next = nextLocal.atZone(SEOUL).toInstant();
			}
		}
		return new MapView.Status(current.zoneCount(),
			current.initialTierZoneCount(), current.missingInitialTierZoneCount(),
			current.voteBasedZoneCount(), current.collectingZoneCount(),
			current.initialGradeCounts(), current.validVoteCount(),
			current.participantCount(), current.lastCompletedAt(), next);
	}

	private static String normalize(String value) {
		return value.replaceAll("\\s+", "").toLowerCase(Locale.ROOT);
	}

	private static int matchPosition(String value, String term) {
		int position = value.indexOf(term);
		return position < 0 ? Integer.MAX_VALUE : position;
	}
}
