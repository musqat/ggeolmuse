package com.muscat.backtest.domain.model;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import com.muscat.backtest.ServiceJson;
import com.muscat.backtest.common.enums.type.TradeReason;
import com.muscat.backtest.common.enums.type.TransactionKind;
import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.List;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import tools.jackson.databind.JsonNode;
import tools.jackson.databind.json.JsonMapper;

@DisplayName("StrategyTransaction 종류 · 팩터리 · 사본 · 응답 JSON · 매수 원가")
class StrategyTransactionTest {

  // 응답에 쓰는 서비스 JSON 설정
  private final JsonMapper objectMapper = ServiceJson.mapper();

  private static StrategyTransaction.StrategyTransactionBuilder buy() {
    LocalDate date = LocalDate.of(2024, 3, 4);
    return StrategyTransaction.builder().kind(TransactionKind.BUY).date(date).actualDate(date).price(BigDecimal.TEN).shares(BigDecimal.ONE)
      .amount(new BigDecimal("13000")).fxRate(new BigDecimal("1300")).trigger("월정액");
  }

  @Test
  @DisplayName("단일 전략 거래에는 종목 · 이유 칸이 없다")
  void singleStrategyHasNoSymbolOrReason() {
    JsonNode json = objectMapper.valueToTree(buy().build());

    assertThat(json.has("symbol")).isFalse();
    assertThat(json.has("reason")).isFalse();
    assertThat(json.get("trigger").asText()).isEqualTo("월정액");
  }

  @Test
  @DisplayName("매수 원가는 넣은 원화를 거래일 환율로 바꾼 값에 쓴 현금을 더하고, 넣은 원화가 없으면 쓴 현금만이다")
  void costUsd() {
    StrategyTransaction deposit = buy().cashUsed(new BigDecimal("50")).build();
    StrategyTransaction noAmount = buy().amount(null).cashUsed(new BigDecimal("50")).build();

    assertThat(deposit.costUsd()).isEqualByComparingTo("60");
    assertThat(noAmount.costUsd()).isEqualByComparingTo("50");
  }

  @Test
  @DisplayName("포트폴리오 거래는 종목 · 이유를 싣는다")
  void portfolioHasSymbolAndReason() {
    JsonNode json = objectMapper.valueToTree(buy().symbol("SPY").reason(TradeReason.DCA_BUY).build());

    assertThat(json.get("symbol").asText()).isEqualTo("SPY");
    assertThat(json.get("reason").asText()).isEqualTo("DCA_BUY");
  }

  @Test
  @DisplayName("응답 JSON 은 거래 칸을 종목부터 낸 양도세까지 정한 순서로 싣고 계산용 값은 싣지 않음")
  void responseJson() throws Exception {
    LocalDate date = LocalDate.of(2024, 3, 4);
    StrategyTransaction sale = StrategyTransaction.builder().kind(TransactionKind.SELL).symbol("SPY").reason(TradeReason.TAKE_PROFIT).date(date)
      .actualDate(date.plusDays(1)).price(new BigDecimal("120.5")).shares(new BigDecimal("-4.00000000"))
      .amount(BigDecimal.ZERO).fxRate(new BigDecimal("1350")).trigger("10.0%익절").fee(new BigDecimal("0.48"))
      .proceeds(new BigDecimal("481.52")).realizedProfit(new BigDecimal("81.52")).defaultFxDate(date.plusDays(1))
      .build();

    assertThat(objectMapper.writeValueAsString(sale)).isEqualTo("{\"symbol\":\"SPY\",\"reason\":\"TAKE_PROFIT\","
      + "\"date\":\"2024-03-04\",\"actualDate\":\"2024-03-05\",\"price\":120.5,\"shares\":-4.00000000,\"amount\":0,"
      + "\"fxRate\":1350,\"trigger\":\"10.0%익절\",\"fee\":0.48,\"proceeds\":481.52,\"cashUsed\":null,"
      + "\"realizedProfit\":81.52,\"taxPaid\":null}");
  }

  private static final LocalDate DAY = LocalDate.of(2024, 3, 4);
  private static final BigDecimal FX = new BigDecimal("1350");

  // 종목 · 이유 없이 계획일 DAY · 체결일 다음 날 · 120.5달러 · 1350원
  private static StrategyTransaction.Order order(String trigger) {
    return new StrategyTransaction.Order(null, null, DAY, DAY.plusDays(1), new BigDecimal("120.5"), FX, null, trigger);
  }

  // order 와 같은 칸을 채운 매수 빌더
  private static StrategyTransaction.StrategyTransactionBuilder same(String trigger) {
    return StrategyTransaction.builder().kind(TransactionKind.BUY).date(DAY).actualDate(DAY.plusDays(1)).price(new BigDecimal("120.5")).fxRate(FX)
      .trigger(trigger);
  }

  private String json(StrategyTransaction tx) throws Exception {
    return objectMapper.writeValueAsString(tx);
  }

  @Test
  @DisplayName("매수 · 매도 팩터리는 종류를 남기고 JSON 은 같은 칸을 채운 거래와 같음(매도 수량은 음수, 새 돈 0)")
  void buyAndSell() throws Exception {
    StrategyTransaction buy = StrategyTransaction.buy(order("월정액"), new BigDecimal("9.99"), new BigDecimal("1.00"),
      new BigDecimal("1300000"), null);
    StrategyTransaction sale = StrategyTransaction.sell(order("10.0%익절"), new BigDecimal("4"),
      new BigDecimal("0.48"), new BigDecimal("481.52"), new BigDecimal("81.52"));

    assertThat(buy.kind()).isEqualTo(TransactionKind.BUY);
    assertThat(json(buy)).isEqualTo(json(same("월정액").shares(new BigDecimal("9.99")).fee(new BigDecimal("1.00"))
      .amount(new BigDecimal("1300000")).build())).doesNotContain("kind");
    assertThat(sale.kind()).isEqualTo(TransactionKind.SELL);
    assertThat(sale.isSell()).isTrue();
    assertThat(json(sale)).isEqualTo(json(same("10.0%익절").shares(new BigDecimal("-4")).fee(new BigDecimal("0.48"))
      .amount(BigDecimal.ZERO).proceeds(new BigDecimal("481.52")).realizedProfit(new BigDecimal("81.52")).build()));
  }

  @Test
  @DisplayName("양도세 납부 · 배당 재투자 팩터리는 수량 0 · 값 0 납부 줄과 수수료 없는 재투자 줄")
  void taxPaymentAndReinvestment() throws Exception {
    StrategyTransaction payment = StrategyTransaction.taxPayment(DAY, FX, new BigDecimal("258.92"), DAY);
    StrategyTransaction reinvested = StrategyTransaction.reinvestment(order("배당 재투자"), new BigDecimal("0.1"),
      new BigDecimal("12.05"));

    assertThat(payment.kind()).isEqualTo(TransactionKind.TAX_PAYMENT);
    assertThat(payment.isTaxPayment()).isTrue();
    assertThat(payment.getDefaultFxDate()).isEqualTo(DAY);
    assertThat(json(payment)).isEqualTo(json(StrategyTransaction.builder().kind(TransactionKind.TAX_PAYMENT).date(DAY).actualDate(DAY)
      .price(BigDecimal.ZERO).shares(BigDecimal.ZERO).amount(BigDecimal.ZERO).fee(BigDecimal.ZERO).fxRate(FX)
      .taxPaid(new BigDecimal("258.92")).trigger("양도세 납부").build()));
    assertThat(reinvested.kind()).isEqualTo(TransactionKind.DIVIDEND_REINVESTMENT);
    assertThat(reinvested.isReinvestment()).isTrue();
    assertThat(json(reinvested)).isEqualTo(json(same("배당 재투자").shares(new BigDecimal("0.1")).amount(BigDecimal.ZERO)
      .cashUsed(new BigDecimal("12.05")).build()));
  }

  @Test
  @DisplayName("연말 정리 팩터리는 매도 · 다시 산 거래 모두 연말 정리이고 매도만 매도로 셈")
  void harvest() throws Exception {
    StrategyTransaction sale = StrategyTransaction.harvestSale(order("공제 채우기 매도"), new BigDecimal("4"),
      new BigDecimal("0.60"), new BigDecimal("599.40"), new BigDecimal("199.40"));
    StrategyTransaction rebuy = StrategyTransaction.harvestBuy(order("공제 채우기 재매수"), new BigDecimal("4.97"),
      new BigDecimal("0.60"), new BigDecimal("599.40"));

    assertThat(List.of(sale.kind(), rebuy.kind()))
      .containsExactly(TransactionKind.HARVEST_SELL, TransactionKind.HARVEST_BUY);
    assertThat(List.of(sale.isYearEndHarvest(), sale.isSell(), rebuy.isYearEndHarvest(), rebuy.isSell()))
      .containsExactly(true, true, true, false);
    assertThat(json(sale)).isEqualTo(json(same("공제 채우기 매도").shares(new BigDecimal("-4")).fee(new BigDecimal("0.60"))
      .amount(BigDecimal.ZERO).proceeds(new BigDecimal("599.40")).realizedProfit(new BigDecimal("199.40")).build()));
    assertThat(json(rebuy)).isEqualTo(json(same("공제 채우기 재매수").shares(new BigDecimal("4.97"))
      .fee(new BigDecimal("0.60")).amount(BigDecimal.ZERO).cashUsed(new BigDecimal("599.40")).build()));
  }

  @Test
  @DisplayName("사본 메서드는 종류를 두고 낸 양도세 · 이유 · 종목 · 기본 환율 날만 바꾼 새 거래")
  void copies() {
    StrategyTransaction payment = StrategyTransaction.taxPayment(DAY, FX, new BigDecimal("100"), null);
    StrategyTransaction sale = StrategyTransaction.harvestSale(order("공제 채우기 매도"), BigDecimal.ONE, BigDecimal.ZERO,
      BigDecimal.TEN, BigDecimal.ONE);

    StrategyTransaction merged = payment.withTaxPaid(new BigDecimal("258.92")).withReason(TradeReason.TAX_PAYMENT);
    StrategyTransaction labeled = sale.labeled("SPY", TradeReason.HARVEST_GAIN_SELL).withDefaultFxDate(DAY);

    assertThat(payment.getTaxPaid()).isEqualByComparingTo("100");
    assertThat(payment.getReason()).isNull();
    assertThat(merged.getTaxPaid()).isEqualByComparingTo("258.92");
    assertThat(merged.getReason()).isEqualTo(TradeReason.TAX_PAYMENT);
    assertThat(merged.kind()).isEqualTo(TransactionKind.TAX_PAYMENT);
    assertThat(sale.getSymbol()).isNull();
    assertThat(List.of(labeled.getSymbol(), labeled.getReason(), labeled.getDefaultFxDate(), labeled.kind()))
      .containsExactly("SPY", TradeReason.HARVEST_GAIN_SELL, DAY, TransactionKind.HARVEST_SELL);
  }

  @Test
  @DisplayName("빌더 거래는 종류를 비울 수 없고 이유 문구와 상관없이 준 종류")
  void builderKind() {
    assertThatThrownBy(() -> same("데드 크로스").kind(null).shares(BigDecimal.ONE.negate()).build())
      .isInstanceOf(NullPointerException.class);
    assertThat(same("손실 상계 매도").kind(TransactionKind.SELL).shares(BigDecimal.ONE.negate()).build().kind())
      .isEqualTo(TransactionKind.SELL);
  }
}
