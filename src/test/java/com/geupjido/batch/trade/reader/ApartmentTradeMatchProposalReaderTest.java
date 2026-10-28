package com.geupjido.batch.trade.reader;

import java.nio.file.Files;
import java.nio.file.Path;
import java.time.YearMonth;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import com.geupjido.batch.trade.model.ApartmentTradeCandidateAssessment.Status;
import com.geupjido.batch.trade.model.ApartmentTradeMatchProposal.Decision;
import com.geupjido.batch.trade.model.ApartmentTradeMatchProposal.Key;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

class ApartmentTradeMatchProposalReaderTest {

	private final ApartmentTradeMatchProposalReader reader =
		new ApartmentTradeMatchProposalReader();

	@Test
	void 용산_표본의_고유_거래단지_제안_14건을_읽는다() {
		var proposals = reader.read(Path.of(
			"data/trade/matching/yongsan-202609-proposals.tsv"
		));

		assertThat(proposals).hasSize(14);
		assertThat(proposals.values().stream()
			.filter(proposal -> proposal.decision() == Decision.APPROVED))
			.hasSize(12);
		assertThat(proposals.values().stream()
			.filter(proposal -> proposal.decision() == Decision.PENDING))
			.hasSize(2);
		assertThat(proposals.values().stream()
			.filter(proposal -> proposal.assessmentStatus() == Status.NAME_EXACT))
			.hasSize(3);
		assertThat(proposals.get(new Key(
			YearMonth.of(2026, 9), "1117013100", "한남동", "29-4",
			"한성1", (short) 1974
		)).decision()).isEqualTo(Decision.PENDING);
		assertThat(proposals.get(new Key(
			YearMonth.of(2026, 9), "1117011200", "원효로1가",
			"133-3", "리첸시아용산A",
			(short) 2010
		)).decision()).isEqualTo(Decision.PENDING);
	}

	@Test
	void 동일한_원본_키의_중복_제안은_거부한다(@TempDir Path dir)
		throws Exception {
		Path file = dir.resolve("duplicate.tsv");
		String original = Files.readString(Path.of(
			"data/trade/matching/yongsan-202609-proposals.tsv"
		));
		String firstRow = original.lines().skip(1).findFirst().orElseThrow();
		Files.writeString(file, original + firstRow + "\n");

		assertThatThrownBy(() -> reader.read(file))
			.isInstanceOf(IllegalArgumentException.class)
			.hasMessageContaining("중복된 단지 연결 검토 항목");
	}

	@Test
	void 같은_원본_조합이라도_다른_계약월은_별도_승인_항목이다(
		@TempDir Path dir
	) throws Exception {
		Path file = dir.resolve("different-month.tsv");
		String original = Files.readString(Path.of(
			"data/trade/matching/yongsan-202609-proposals.tsv"
		));
		String octoberRow = original.lines().skip(1).findFirst()
			.orElseThrow().replaceFirst("^202609", "202610");
		Files.writeString(file, original + octoberRow + "\n");

		assertThat(reader.read(file)).hasSize(15);
	}

	@Test
	void 잘못된_계약월은_거부한다(@TempDir Path dir) throws Exception {
		Path file = dir.resolve("invalid-month.tsv");
		String original = Files.readString(Path.of(
			"data/trade/matching/yongsan-202609-proposals.tsv"
		));
		Files.writeString(file, original.replaceFirst("202609", "202613"));

		assertThatThrownBy(() -> reader.read(file))
			.isInstanceOf(IllegalArgumentException.class)
			.hasMessageContaining("값이 잘못되었습니다");
	}
}
