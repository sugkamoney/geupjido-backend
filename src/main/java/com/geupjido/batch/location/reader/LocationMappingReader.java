package com.geupjido.batch.location.reader;

import com.fasterxml.jackson.databind.DeserializationFeature;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.geupjido.batch.location.exception.LocationMappingException;
import com.geupjido.batch.location.model.LocationMapping;
import org.springframework.stereotype.Component;

import java.io.IOException;
import java.nio.file.Path;

/**
 * JSON 매핑 파일을 읽고 지역·권역 매핑 구조로 변환한다.
 */
@Component
public class LocationMappingReader {

	private final ObjectMapper objectMapper;

	public LocationMappingReader(ObjectMapper objectMapper) {
		this.objectMapper = objectMapper.copy()
			.enable(DeserializationFeature.FAIL_ON_UNKNOWN_PROPERTIES);
	}

	public LocationMapping read(Path path) {
		try {
			LocationMapping mapping = objectMapper.readValue(path.toFile(), LocationMapping.class);
			mapping.validate();
			return mapping;
		} catch (LocationMappingException exception) {
			throw exception;
		} catch (IOException exception) {
			throw new LocationMappingException(
				"지역·권역 매핑 파일을 읽을 수 없습니다: " + path, exception
			);
		}
	}
}
