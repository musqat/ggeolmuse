package com.muscat.user.infra.client;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.BDDMockito.given;
import static org.mockito.Mockito.mock;

import com.muscat.commonlib.dto.FxRateDto;
import com.muscat.commonlib.dto.FxRateRangeDto;
import java.math.BigDecimal;
import java.time.LocalDate;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.test.util.ReflectionTestUtils;

// 서킷 브레이커의 대체 메서드는 private 이라 직접 호출
@DisplayName("시세 서비스 환율 호출과 대체값")
class MarketDataServiceClientWrapperTest {

  private final MarketDataServiceClient client = mock(MarketDataServiceClient.class);
  private final MarketDataServiceClientWrapper wrapper = new MarketDataServiceClientWrapper(client);

  @Test
  @DisplayName("정상일 때는 시세 서비스가 준 값을 그대로 돌려준다")
  void delegates() {
    FxRateDto rate = new FxRateDto(LocalDate.of(2024, 3, 15), new BigDecimal("1330"), "KOREAEXIM");
    FxRateRangeDto range = new FxRateRangeDto(new BigDecimal("1053.37"), LocalDate.of(2024, 1, 2),
      new BigDecimal("1554.10"), LocalDate.of(2026, 7, 2));
    given(client.getLatestFxRate()).willReturn(rate);
    given(client.getFxRateRange()).willReturn(range);

    assertThat(wrapper.getLatestFxRate()).isSameAs(rate);
    assertThat(wrapper.getFxRateRange()).isSameAs(range);
  }

  @Test
  @DisplayName("최신 환율 · 범위를 못 받으면 지어낸 값 없이 비워 둔다")
  void latestAndRangeFallback() {
    FxRateDto latest = ReflectionTestUtils.invokeMethod(wrapper, "getLatestFxRateFallback", new RuntimeException("down"));
    FxRateRangeDto range = ReflectionTestUtils.invokeMethod(wrapper, "getFxRateRangeFallback", new RuntimeException("down"));

    assertThat(latest).isNull();
    assertThat(range).isNull();
  }
}
