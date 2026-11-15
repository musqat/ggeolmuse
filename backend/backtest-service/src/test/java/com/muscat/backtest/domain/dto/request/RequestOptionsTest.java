package com.muscat.backtest.domain.dto.request;

import static org.assertj.core.api.Assertions.assertThat;

import com.muscat.backtest.common.enums.type.PeriodEnd;
import java.math.BigDecimal;
import java.util.List;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

@DisplayName("RequestOptions 요청 공통 옵션")
class RequestOptionsTest {

  @Test
  @DisplayName("적립식 · 조건부 · 이동평균 교차 · 밴드 · 시뮬레이션 요청은 빌더 · 기본 생성자 모두 재투자 끔 · 수수료율 0 · 원천징수 0 이고 나머지 공통 옵션은 비움")
  void sharedDefaults() {
    List<RequestOptions> requests = List.of(DcaStrategyRequest.builder().build(),
      ConditionalStrategyRequest.builder().build(), MaCrossStrategyRequest.builder().build(),
      MaBandStrategyRequest.builder().build(), SimulationRequest.builder().build(), new DcaStrategyRequest(),
      new ConditionalStrategyRequest(), new MaCrossStrategyRequest(), new MaBandStrategyRequest(),
      new SimulationRequest());

    for (RequestOptions request : requests) {
      assertThat(request.getReinvestDividends()).as(request.getClass().getSimpleName()).isFalse();
      assertThat(request.getTradingFeeRate()).as(request.getClass().getSimpleName()).isEqualTo(BigDecimal.ZERO);
      assertThat(request.getDividendTaxRate()).as(request.getClass().getSimpleName()).isEqualTo(BigDecimal.ZERO);
      assertThat(List.of(request.getPurchaseFxRate() == null, request.getCurrentFxRate() == null,
        request.getCapitalGainsTax() == null, request.getPeriodEnd() == null, request.getYearEndHarvest() == null))
        .as(request.getClass().getSimpleName()).containsOnly(true);
    }
  }

  @Test
  @DisplayName("빌더는 공통 옵션과 종목을 같이 채우고 연말 정리 사본은 공통 옵션 · 종목을 그대로 두고 연말 정리 · 기간 끝 매도를 켜고 사용자 아이디를 비우며 원래 요청은 바꾸지 않음")
  void builderAndVariant() {
    ConditionalStrategyRequest request = ConditionalStrategyRequest.builder().tradingFeeRate(new BigDecimal("0.0025"))
      .symbol("AAPL").capitalGainsTax(true).userId("user-1").build();

    ConditionalStrategyRequest variant = request.yearEndVariant(true);

    assertThat(variant.getSymbol()).isEqualTo("AAPL");
    assertThat(variant.getTradingFeeRate()).isEqualByComparingTo("0.0025");
    assertThat(variant.getCapitalGainsTax()).isTrue();
    assertThat(variant.getYearEndHarvest()).isTrue();
    assertThat(variant.getPeriodEnd()).isEqualTo(PeriodEnd.SELL_AT_END);
    assertThat(variant.getUserId()).isNull();
    assertThat(request.getYearEndHarvest()).isNull();
    assertThat(request.getPeriodEnd()).isNull();
  }

  @Test
  @DisplayName("공통 옵션 하나만 바꾼 요청은 원래 요청과 같지 않고 문자열에 공통 옵션이 들어감")
  void equalityCountsOptions() {
    MaCrossStrategyRequest plain = MaCrossStrategyRequest.builder().symbol("SPY").build();
    MaCrossStrategyRequest charged = MaCrossStrategyRequest.builder().symbol("SPY")
      .tradingFeeRate(new BigDecimal("0.001")).build();

    assertThat(plain).isEqualTo(MaCrossStrategyRequest.builder().symbol("SPY").build()).isNotEqualTo(charged);
    assertThat(charged.toString()).contains("tradingFeeRate=0.001");
  }
}
