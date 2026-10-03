package com.geupjido.batch.trade.mapper;

import java.math.BigDecimal;
import java.time.DateTimeException;
import java.time.LocalDate;
import java.time.format.DateTimeFormatter;
import java.time.format.DateTimeParseException;
import java.time.format.ResolverStyle;

import org.springframework.stereotype.Component;

import com.geupjido.batch.trade.dto.ApartmentTradeApiItem;
import com.geupjido.batch.trade.exception.ApartmentTradeMappingException;
import com.geupjido.batch.trade.model.ApartmentTradeData;

/**
 * 아파트 매매 실거래가 API 응답을 내부 거래 모델로 변환한다.
 */
@Component
public class ApartmentTradeMapper {

	private static final DateTimeFormatter CANCELLATION_DATE_FORMATTER =
		DateTimeFormatter.ofPattern("uu.MM.dd")
			.withResolverStyle(ResolverStyle.STRICT);

	/**
	 * 아파트 매매 실거래가 API 응답 한 건을 내부 도메인 모델로 변환한다.
	 */
	public ApartmentTradeData map(ApartmentTradeApiItem item) {
		if (item == null) {
			throw new ApartmentTradeMappingException(
				"변환할 아파트 매매 실거래가가 없습니다."
			);
		}

		boolean canceled = toCanceled(item.cdealType());
		LocalDate cancellationDate =
			toCancellationDate(item.cdealDay());

		validateCancellation(canceled, cancellationDate);

		return new ApartmentTradeData(
			normalizeRequiredText(item.sggCd(), "시군구 코드"),
			normalizeRequiredText(item.umdNm(), "법정동명"),
			normalizeRequiredText(item.aptNm(), "아파트명"),
			normalizeNullableText(item.jibun()),
			normalizeNullableText(item.aptDong()),
			toNullableInteger(item.buildYear(), "건축연도"),
			toDealDate(
				item.dealYear(),
				item.dealMonth(),
				item.dealDay()
			),
			toDealAmountTenThousandWon(item.dealAmount()),
			toExclusiveArea(item.excluUseAr()),
			toNullableInteger(item.floor(), "층"),
			normalizeNullableText(item.dealingGbn()),
			canceled,
			cancellationDate
		);
	}

	private String normalizeRequiredText(
		String value,
		String label
	) {
		if (value == null || value.isBlank()) {
			throw new ApartmentTradeMappingException(
				label + "이(가) 없습니다."
			);
		}

		return value.trim();
	}

	private String normalizeNullableText(String value) {
		if (value == null || value.isBlank()) {
			return null;
		}

		return value.trim();
	}

	private long toDealAmountTenThousandWon(String value) {
		String normalized = normalizeRequiredText(
			value,
			"거래금액"
		).replace(",", "");

		try {
			long amount = Long.parseLong(normalized);

			if (amount <= 0) {
				throw new ApartmentTradeMappingException(
					"거래금액은 0보다 커야 합니다: " + value
				);
			}

			return amount;
		} catch (NumberFormatException exception) {
			throw new ApartmentTradeMappingException(
				"거래금액을 숫자로 변환할 수 없습니다: " + value,
				exception
			);
		}
	}

	private BigDecimal toExclusiveArea(String value) {
		String normalized = normalizeNullableText(value);

		if (normalized == null) {
			return null;
		}

		try {
			BigDecimal area = new BigDecimal(normalized);

			if (area.signum() <= 0) {
				throw new ApartmentTradeMappingException(
					"전용면적은 0보다 커야 합니다: " + value
				);
			}

			return area;
		} catch (NumberFormatException exception) {
			throw new ApartmentTradeMappingException(
				"전용면적을 숫자로 변환할 수 없습니다: " + value,
				exception
			);
		}
	}

	private Integer toNullableInteger(
		String value,
		String label
	) {
		String normalized = normalizeNullableText(value);

		if (normalized == null) {
			return null;
		}

		try {
			return Integer.valueOf(normalized);
		} catch (NumberFormatException exception) {
			throw new ApartmentTradeMappingException(
				label + "을(를) 정수로 변환할 수 없습니다: " + value,
				exception
			);
		}
	}

	private LocalDate toDealDate(
		String yearValue,
		String monthValue,
		String dayValue
	) {
		String year = normalizeRequiredText(
			yearValue,
			"계약연도"
		);
		String month = normalizeRequiredText(
			monthValue,
			"계약월"
		);
		String day = normalizeRequiredText(
			dayValue,
			"계약일"
		);

		try {
			return LocalDate.of(
				Integer.parseInt(year),
				Integer.parseInt(month),
				Integer.parseInt(day)
			);
		} catch (
			NumberFormatException | DateTimeException exception
		) {
			throw new ApartmentTradeMappingException(
				"계약일을 날짜로 변환할 수 없습니다: "
					+ yearValue + "-"
					+ monthValue + "-"
					+ dayValue,
				exception
			);
		}
	}

	private LocalDate toCancellationDate(String value) {
		String normalized = normalizeNullableText(value);

		if (normalized == null) {
			return null;
		}

		try {
			return LocalDate.parse(
				normalized,
				CANCELLATION_DATE_FORMATTER
			);
		} catch (DateTimeParseException exception) {
			throw new ApartmentTradeMappingException(
				"해제사유발생일을 날짜로 변환할 수 없습니다: " + value,
				exception
			);
		}
	}

	private boolean toCanceled(String value) {
		String normalized = normalizeNullableText(value);

		if (normalized == null) {
			return false;
		}

		if ("O".equalsIgnoreCase(normalized)) {
			return true;
		}

		throw new ApartmentTradeMappingException(
			"알 수 없는 거래 해제 여부 값입니다: " + value
		);
	}

	private void validateCancellation(
		boolean canceled,
		LocalDate cancellationDate
	) {
		if (canceled && cancellationDate == null) {
			throw new ApartmentTradeMappingException(
				"해제된 거래에는 해제사유발생일이 필요합니다."
			);
		}

		if (!canceled && cancellationDate != null) {
			throw new ApartmentTradeMappingException(
				"정상 거래에는 해제사유발생일이 있을 수 없습니다."
			);
		}
	}
}
