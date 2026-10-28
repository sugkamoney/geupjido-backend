package com.geupjido.batch.location.reader;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.geupjido.batch.location.exception.LocationMappingException;
import com.geupjido.batch.location.model.LegalDongCodeReference;
import com.geupjido.batch.location.model.LocationMapping;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

class LegalDongCodeReferenceReaderTest {

	@TempDir
	Path tempDirectory;

	private final LegalDongCodeReferenceReader reader =
		new LegalDongCodeReferenceReader();

	@Test
	void 실제_참조_파일은_매핑된_법정동과_대상_밖_법정동을_찾는다() {
		LegalDongCodeReference reference = reader.read(Path.of(
			"data/location/reference/legal-dongs-20260917.tsv"
		));
		LocationMapping mapping = new LocationMappingReader(
			new ObjectMapper()
		).read(Path.of("data/location/mapping/location-mapping.json"));

		assertThat(reference.codes()).hasSize(1370);
		assertThat(reference.findCode("11170", "한남동"))
			.contains("1117013100");
		assertThat(reference.findCode("11170", "동자동"))
			.contains("1117010700");
		assertThat(reference.findCode("11170", "없는동"))
			.isEmpty();

		for (LocationMapping.ZoneDefinition zone : mapping.zones()) {
			for (String code : zone.dongCodes()) {
				assertThat(reference.codes()).containsValue(code);
			}
		}
	}

	@Test
	void 같은_시군구와_동_이름이_중복되면_실패한다() throws IOException {
		Path path = tempDirectory.resolve("duplicate.tsv");
		Files.writeString(path, """
			법정동코드\t법정동명
			1117013100\t서울특별시 용산구 한남동
			1117013200\t서울특별시 용산구 한남동
			""");

		assertThatThrownBy(() -> reader.read(path))
			.isInstanceOf(LocationMappingException.class)
			.hasMessageContaining("중복 코드 또는 이름");
	}
}
