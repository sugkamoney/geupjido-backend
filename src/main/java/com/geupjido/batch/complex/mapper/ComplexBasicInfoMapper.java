package com.geupjido.batch.complex.mapper;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.time.format.DateTimeFormatter;
import java.time.format.DateTimeParseException;

import org.springframework.stereotype.Component;

import com.geupjido.batch.complex.dto.ComplexBasicInfoApiItem;
import com.geupjido.batch.complex.exception.ComplexBasicInfoMappingException;
import com.geupjido.complex.domain.Complex;

/**
 * 공동주택 기본정보 API 응답을 단지 도메인 객체로 변환한다.
 */
@Component
public class ComplexBasicInfoMapper {

	private static final DateTimeFormatter APPROVAL_DATE_FORMATTER =
		DateTimeFormatter.BASIC_ISO_DATE;

	public Complex map(ComplexBasicInfoApiItem item) {
		if (item == null) {
			throw new ComplexBasicInfoMappingException(
				"변환할 공동주택 기본정보가 없습니다."
			);
		}

		return new Complex(
			item.kaptCode(),
			item.kaptName(),
			item.kaptAddr(),
			normalizeNullableText(item.doroJuso()),
			toHouseholds(item.kaptdaCnt()),
			toBuildingCount(item.kaptDongCnt()),
			toApprovalDate(item.kaptUsedate())
		);
	}

	private int toHouseholds(BigDecimal value) {
		if (value == null) {
			throw new ComplexBasicInfoMappingException(
				"세대수가 없습니다."
			);
		}

		try {
			return value.intValueExact();
		} catch (ArithmeticException exception) {
			throw new ComplexBasicInfoMappingException(
				"세대수를 정수로 변환할 수 없습니다: " + value,
				exception
			);
		}
	}

	private Integer toBuildingCount(String value) {
		if (value == null || value.isBlank()) {
			return null;
		}

		try {
			return Integer.valueOf(value.trim());
		} catch (NumberFormatException exception) {
			throw new ComplexBasicInfoMappingException(
				"동 수를 정수로 변환할 수 없습니다: " + value,
				exception
			);
		}
	}

	private LocalDate toApprovalDate(String value) {
		if (value == null || value.isBlank()) {
			return null;
		}

		try {
			return LocalDate.parse(
				value.trim(),
				APPROVAL_DATE_FORMATTER
			);
		} catch (DateTimeParseException exception) {
			throw new ComplexBasicInfoMappingException(
				"사용승인일을 날짜로 변환할 수 없습니다: " + value,
				exception
			);
		}
	}

	private String normalizeNullableText(String value) {
		if (value == null || value.isBlank()) {
			return null;
		}

		return value.trim();
	}
}
