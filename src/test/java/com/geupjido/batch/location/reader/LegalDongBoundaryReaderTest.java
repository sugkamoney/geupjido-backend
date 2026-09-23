package com.geupjido.batch.location.reader;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.geupjido.batch.location.model.LegalDongBoundary;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;

class LegalDongBoundaryReaderTest {

	@TempDir
	Path tempDirectory;

	private final LegalDongBoundaryReader reader =
		new LegalDongBoundaryReader(new ObjectMapper());

	@Test
	void FeatureCollection에서_법정동_경계를_읽는다() throws IOException {
		Path path = tempDirectory.resolve("legal-dong-boundary.geojson");

		Files.writeString(path, """
			{
			  "type": "FeatureCollection",
			  "features": [
			    {
			      "type": "Feature",
			      "properties": {
			        "EMD_CD": "1168011000",
			        "EMD_NM": "압구정동"
			      },
			      "geometry": {
			        "type": "Polygon",
			        "coordinates": [
			          [
			            [127.00, 37.50],
			            [127.01, 37.50],
			            [127.01, 37.51],
			            [127.00, 37.51],
			            [127.00, 37.50]
			          ]
			        ]
			      }
			    }
			  ]
			}
			""");

		List<LegalDongBoundary> boundaries = reader.read(
			path,
			"EMD_CD"
		);

		assertThat(boundaries).hasSize(1);
		assertThat(boundaries.get(0).dongCode())
			.isEqualTo("1168011000");
		assertThat(boundaries.get(0).geometryJson())
			.contains("\"type\":\"Polygon\"");
	}
}
