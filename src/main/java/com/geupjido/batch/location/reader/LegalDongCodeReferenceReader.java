package com.geupjido.batch.location.reader;

import java.io.BufferedReader;
import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.HashMap;
import java.util.HashSet;
import java.util.Map;
import java.util.Set;

import org.springframework.stereotype.Component;

import com.geupjido.batch.location.exception.LocationMappingException;
import com.geupjido.batch.location.model.LegalDongCodeReference;
import com.geupjido.batch.location.model.LegalDongCodeReference.Key;

/**
 * 행정안전부 법정동 코드에서 추린 TSV 참조 파일을 읽는다.
 */
@Component
public class LegalDongCodeReferenceReader {

	private static final String HEADER = "법정동코드\t법정동명";

	public LegalDongCodeReference read(Path path) {
		if (path == null) {
			throw new LocationMappingException("법정동 참조 파일 경로가 없습니다.");
		}

		try (BufferedReader reader = Files.newBufferedReader(
			path, StandardCharsets.UTF_8
		)) {
			if (!HEADER.equals(reader.readLine())) {
				throw new LocationMappingException(
					"법정동 참조 파일의 열 구성이 예상과 다릅니다."
				);
			}

			Map<Key, String> codes = new HashMap<>();
			Set<String> seenCodes = new HashSet<>();
			String line;
			int lineNumber = 1;

			while ((line = reader.readLine()) != null) {
				lineNumber++;
				String[] columns = line.split("\t", -1);
				if (columns.length != 2) {
					throw new LocationMappingException(
						"법정동 참조 파일의 열 개수가 잘못되었습니다: "
							+ lineNumber
					);
				}

				String code = columns[0];
				String[] nameParts = columns[1].trim().split("\\s+");
				if (!code.matches("[0-9]{10}") || nameParts.length < 3) {
					throw new LocationMappingException(
						"법정동 참조 파일의 코드 또는 이름이 잘못되었습니다: "
							+ lineNumber
					);
				}

				Key key = new Key(code.substring(0, 5),
					nameParts[nameParts.length - 1]);
				if (!seenCodes.add(code) || codes.putIfAbsent(key, code) != null) {
					throw new LocationMappingException(
						"법정동 참조 파일에 중복 코드 또는 이름이 있습니다: "
							+ lineNumber
					);
				}
			}

			if (codes.isEmpty()) {
				throw new LocationMappingException("법정동 참조 파일이 비어 있습니다.");
			}

			return new LegalDongCodeReference(codes);
		} catch (IOException exception) {
			throw new LocationMappingException(
				"법정동 참조 파일을 읽을 수 없습니다: " + path,
				exception
			);
		}
	}
}
