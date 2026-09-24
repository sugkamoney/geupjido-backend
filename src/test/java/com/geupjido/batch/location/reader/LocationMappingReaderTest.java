package com.geupjido.batch.location.reader;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.geupjido.batch.location.exception.LocationMappingException;
import com.geupjido.batch.location.model.LocationMapping;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

class LocationMappingReaderTest {

	@TempDir
	Path tempDirectory;

	private final LocationMappingReader reader =
		new LocationMappingReader(new ObjectMapper());

	@Test
	void JSON_매핑_파일을_읽는다() throws IOException {
		Path path = tempDirectory.resolve("location-mapping.json");

		Files.writeString(path, """
			{
			  "regions": [
			    {
			      "code": "seoul",
			      "name": "서울",
			      "active": true,
			      "displayOrder": 1
			    }
			  ],
			  "cities": [
			    {
			      "id": "11680",
			      "regionCode": "seoul",
			      "name": "강남구"
			    }
			  ],
			  "zones": [
			    {
			      "id": "gangnam-apgujeong",
			      "cityId": "11680",
			      "name": "압구정",
			      "dongCodes": ["1168011000"],
			      "initialTier": 1.1
			    }
			  ]
			}
			""");

		LocationMapping mapping = reader.read(path);

		assertThat(mapping.regions()).hasSize(1);
		assertThat(mapping.cities()).hasSize(1);
		assertThat(mapping.zones()).hasSize(1);
		assertThat(mapping.zones().get(0).dongCodes())
			.containsExactly("1168011000");
	}

	@Test
	void 실제_지역_매핑_파일을_읽는다() {
		Path path = Path.of(
			"data/location/mapping/location-mapping.json"
		);

		LocationMapping mapping = reader.read(path);

		int assignedDongCodeCount = mapping.zones().stream()
			.mapToInt(zone -> zone.dongCodes().size())
			.sum();

		assertThat(mapping.regions()).hasSize(3);
		assertThat(mapping.cities()).hasSize(40);
		assertThat(mapping.zones()).hasSize(162);
		assertThat(assignedDongCodeCount).isEqualTo(253);
	}

	@Test
	void 알_수_없는_JSON_필드가_있으면_실패한다() throws IOException {
		Path path = tempDirectory.resolve("invalid-location-mapping.json");

		Files.writeString(path, """
		{
		  "regions": [
		    {
		      "code": "seoul",
		      "name": "서울",
		      "active": true,
		      "displayOrder": 1
		    }
		  ],
		  "cities": [
		    {
		      "id": "11680",
		      "regionCode": "seoul",
		      "name": "강남구"
		    }
		  ],
		  "zones": [
		    {
		      "id": "gangnam-apgujeong",
		      "cityId": "11680",
		      "name": "압구정",
		      "dongCodes": ["1168011000"],
		      "initialTier": 1.1
		    }
		  ],
		  "unknownField": true
		}
		""");

		assertThatThrownBy(() -> reader.read(path))
			.isInstanceOf(LocationMappingException.class)
			.hasMessageContaining("매핑 파일을 읽을 수 없습니다");
	}
}
