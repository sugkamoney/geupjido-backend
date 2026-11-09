package com.geupjido.match.runner;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;

import com.geupjido.match.service.WorldcupVoteRatingPublicationService;

@Component
@ConditionalOnProperty(
	prefix = "app.worldcup-rating", name = "schedule-enabled", havingValue = "true"
)
public class WorldcupRatingScheduler {

	private static final Logger log = LoggerFactory.getLogger(WorldcupRatingScheduler.class);

	private final WorldcupVoteRatingPublicationService publicationService;

	public WorldcupRatingScheduler(WorldcupVoteRatingPublicationService publicationService) {
		this.publicationService = publicationService;
	}

	@Scheduled(cron = "${app.worldcup-rating.schedule-cron:0 0 */3 * * *}", zone = "Asia/Seoul")
	public void publish() {
		log.info("월드컵 급지·순위 집계 반영 완료: {}", publicationService.publish());
	}
}
