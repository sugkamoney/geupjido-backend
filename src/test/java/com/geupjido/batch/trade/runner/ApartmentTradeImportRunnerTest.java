package com.geupjido.batch.trade.runner;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.boot.test.context.runner.ApplicationContextRunner;

import com.geupjido.batch.trade.config.ApartmentTradeImportProperties;
import com.geupjido.batch.trade.model.ApartmentTradeImportResult;
import com.geupjido.batch.trade.service.ApartmentTradeImportService;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class ApartmentTradeImportRunnerTest {

	@Mock
	private ApartmentTradeImportService importService;

	@Test
	void 지정한_시군구와_계약월을_한_번_적재한다() {
		ApartmentTradeImportRunner runner = runner("11170", "202609");
		when(importService.importMonth("11170", "202609"))
			.thenReturn(new ApartmentTradeImportResult(1, 1, 0, 0));

		runner.run(null);

		verify(importService).importMonth("11170", "202609");
	}

	@Test
	void 시군구_코드가_없거나_잘못되면_호출하지_않는다() {
		assertThatThrownBy(() -> runner(null, "202609").run(null))
			.isInstanceOf(IllegalStateException.class)
			.hasMessageContaining("시군구 코드");
		assertThatThrownBy(() -> runner("1117", "202609").run(null))
			.isInstanceOf(IllegalStateException.class)
			.hasMessageContaining("숫자 5자리");

		verifyNoInteractions(importService);
	}

	@Test
	void 계약월이_없거나_존재하지_않으면_호출하지_않는다() {
		assertThatThrownBy(() -> runner("11170", null).run(null))
			.isInstanceOf(IllegalStateException.class)
			.hasMessageContaining("계약월");
		assertThatThrownBy(() -> runner("11170", "202613").run(null))
			.isInstanceOf(IllegalStateException.class)
			.hasMessageContaining("yyyyMM");

		verifyNoInteractions(importService);
	}

	@Test
	void 수동_적재가_비활성화되면_Runner를_등록하지_않는다() {
		new ApplicationContextRunner()
			.withUserConfiguration(ApartmentTradeImportRunner.class)
			.withPropertyValues("app.apartment-trade-import.enabled=false")
			.run(context -> assertThat(context)
				.doesNotHaveBean(ApartmentTradeImportRunner.class));
	}

	private ApartmentTradeImportRunner runner(
		String districtCode,
		String dealYearMonth
	) {
		return new ApartmentTradeImportRunner(
			new ApartmentTradeImportProperties(
				true,
				districtCode,
				dealYearMonth
			),
			importService
		);
	}
}
