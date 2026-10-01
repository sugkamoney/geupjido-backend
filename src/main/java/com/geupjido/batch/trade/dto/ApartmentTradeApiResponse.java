package com.geupjido.batch.trade.dto;

import java.util.List;

import com.fasterxml.jackson.annotation.JsonIgnoreProperties;
import com.fasterxml.jackson.dataformat.xml.annotation.JacksonXmlElementWrapper;
import com.fasterxml.jackson.dataformat.xml.annotation.JacksonXmlProperty;
import com.fasterxml.jackson.dataformat.xml.annotation.JacksonXmlRootElement;

/**
 * 아파트 매매 실거래가 API의 전체 XML 응답 구조를 나타낸다.
 */
@JsonIgnoreProperties(ignoreUnknown = true)
@JacksonXmlRootElement(localName = "response")
public record ApartmentTradeApiResponse(
	Header header,
	Body body
) {

	@JsonIgnoreProperties(ignoreUnknown = true)
	public record Header(
		String resultCode,
		String resultMsg
	) {
	}

	@JsonIgnoreProperties(ignoreUnknown = true)
	public record Body(
		Items items,
		int numOfRows,
		int pageNo,
		int totalCount
	) {
		public Body {
			items = items == null ? new Items(List.of()) : items;
		}

		public List<ApartmentTradeApiItem> tradeItems() {
			return items.item();
		}
	}

	@JsonIgnoreProperties(ignoreUnknown = true)
	public record Items(
		@JacksonXmlElementWrapper(useWrapping = false)
		@JacksonXmlProperty(localName = "item")
		List<ApartmentTradeApiItem> item
	) {
		public Items {
			item = item == null ? List.of() : List.copyOf(item);
		}
	}
}
