package com.geupjido.batch.trade.reader;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.time.YearMonth;
import java.util.HashMap;
import java.util.Map;

import org.springframework.stereotype.Component;

import com.geupjido.batch.trade.model.ApartmentTradeCandidateAssessment.Status;
import com.geupjido.batch.trade.model.ApartmentTradeMatchProposal;
import com.geupjido.batch.trade.model.ApartmentTradeMatchProposal.Decision;
import com.geupjido.batch.trade.model.ApartmentTradeMatchProposal.Key;

/**
 * 수동 검토 목록을 읽는다. PENDING 항목은 연결 승인이 아니다.
 */
@Component
public class ApartmentTradeMatchProposalReader {

	private static final String HEADER =
		"계약월\t법정동코드\t법정동명\t지번\t거래단지명\t건축연도\t후보단지ID\t후보단지명\t판정\t결정";

	public Map<Key, ApartmentTradeMatchProposal> read(Path path) {
		if (path == null || !Files.isRegularFile(path)) {
			throw new IllegalArgumentException(
				"단지 연결 검토 파일이 없습니다: " + path
			);
		}

		try (var lines = Files.lines(path, StandardCharsets.UTF_8)) {
			var iterator = lines.iterator();
			if (!iterator.hasNext() || !HEADER.equals(iterator.next())) {
				throw new IllegalArgumentException(
					"단지 연결 검토 파일의 열 구성이 예상과 다릅니다."
				);
			}

			Map<Key, ApartmentTradeMatchProposal> proposals =
				new HashMap<>();
			int lineNumber = 1;
			while (iterator.hasNext()) {
				lineNumber++;
				ApartmentTradeMatchProposal proposal = parse(
					iterator.next(), lineNumber
				);
				if (proposals.putIfAbsent(
					proposal.key(), proposal
				) != null) {
					throw new IllegalArgumentException(
						"중복된 단지 연결 검토 항목입니다: " + lineNumber
					);
				}
			}
			return Map.copyOf(proposals);
		} catch (IOException exception) {
			throw new IllegalStateException(
				"단지 연결 검토 파일을 읽을 수 없습니다: " + path,
				exception
			);
		}
	}

	private static ApartmentTradeMatchProposal parse(
		String line,
		int lineNumber
	) {
		String[] fields = line.split("\t", -1);
		if (fields.length != 10
			|| !fields[0].matches("[0-9]{4}(0[1-9]|1[0-2])")
			|| !fields[1].matches("[0-9]{10}")
			|| fields[2].isBlank()
			|| fields[3].isBlank()
			|| fields[4].isBlank()
			|| !fields[5].matches("[0-9]{4}")
			|| fields[6].isBlank()
			|| fields[7].isBlank()) {
			throw new IllegalArgumentException(
				"단지 연결 검토 항목의 값이 잘못되었습니다: " + lineNumber
			);
		}

		try {
			Status status = Status.valueOf(fields[8]);
			if (status != Status.NAME_EXACT
				&& status != Status.NAME_REVIEW) {
				throw new IllegalArgumentException("판정 상태가 잘못되었습니다.");
			}
			return new ApartmentTradeMatchProposal(
				new Key(
					YearMonth.of(
						Integer.parseInt(fields[0].substring(0, 4)),
						Integer.parseInt(fields[0].substring(4))
					),
					fields[1], fields[2], fields[3], fields[4],
					Short.parseShort(fields[5])
				),
				fields[6], fields[7], status,
				Decision.valueOf(fields[9])
			);
		} catch (IllegalArgumentException exception) {
			throw new IllegalArgumentException(
				"단지 연결 검토 항목의 상태가 잘못되었습니다: "
					+ lineNumber,
				exception
			);
		}
	}
}
