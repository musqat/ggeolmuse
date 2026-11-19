package com.muscat.backtest.domain.model;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.time.LocalDate;
import com.fasterxml.jackson.annotation.JsonIgnore;
import com.fasterxml.jackson.annotation.JsonInclude;
import com.muscat.backtest.common.constants.BacktestConstants.TransactionTrigger;
import com.muscat.backtest.common.enums.type.TradeReason;
import com.muscat.backtest.common.enums.type.TransactionKind;
import lombok.AccessLevel;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.EqualsAndHashCode;
import lombok.Getter;
import lombok.NonNull;
import lombok.ToString;

// 투자 전략 거래 내역
@Getter
@Builder(toBuilder = true)
@AllArgsConstructor(access = AccessLevel.PRIVATE)
@EqualsAndHashCode
@ToString
public class StrategyTransaction {

  // 거래 종류
  @JsonIgnore
  @Getter(AccessLevel.NONE)
  @EqualsAndHashCode.Exclude
  @NonNull
  private final TransactionKind kind;
  @JsonInclude(JsonInclude.Include.NON_NULL)
  private final String symbol;          // 거래 종목 (포트폴리오만)
  @JsonInclude(JsonInclude.Include.NON_NULL)
  private final TradeReason reason;     // 거래 이유 (포트폴리오만)
  private final LocalDate date;         // 계획된 거래일
  private final LocalDate actualDate;   // 실제 거래일 (주말/휴장일 조정)
  private final BigDecimal price;       // USD 주가
  private final BigDecimal shares;      // 매수 · 매도 수량
  private final BigDecimal amount;      // 투자금액 (KRW)
  private final BigDecimal fxRate;      // 거래에 쓴 환율
  private final String trigger;         // 거래 이유 ("월정액" · "5%하락" · "골든 크로스" · "양도세 납부" 등)
  private final BigDecimal fee;         // 매매 수수료 (USD, 배당 재투자는 null)
  private final BigDecimal proceeds;    // 매도로 받은 돈 (USD, 수수료 뺀 값, 매수는 null)
  private final BigDecimal cashUsed;    // 매수에 쓴 현금 (USD, 새 돈만 넣었으면 null)
  private final BigDecimal realizedProfit;  // 매도 실현 손익 (USD, 매수는 null)
  private final BigDecimal taxPaid;     // 현금에서 낸 양도세 (USD, 양도세 납부가 아니면 null)

  // 환율이 없어 기본 환율로 산 날(아니면 null)
  @JsonIgnore
  private final LocalDate defaultFxDate;

  // 거래 종목 · 이유(포트폴리오만) · 계획일 · 실제 거래일 · 값(USD) · 환율 · 기본 환율 날 · 이유 문구
  public record Order(String symbol, TradeReason reason, LocalDate date, LocalDate actualDate, BigDecimal price,
                      BigDecimal fxRate, LocalDate defaultFxDate, String trigger) {}

  // 정한 주식을 산 거래(새로 넣은 원화 · 쓴 현금(USD)이 없으면 null)
  public static StrategyTransaction buy(Order order, BigDecimal shares, BigDecimal fee, BigDecimal amount,
                                        BigDecimal cashUsed) {
    return of(TransactionKind.BUY, order).shares(shares).fee(fee).amount(amount).cashUsed(cashUsed).build();
  }

  // 정한 주식을 판 거래(수량은 음수, 판 돈은 수수료 뺀 USD)
  public static StrategyTransaction sell(Order order, BigDecimal shares, BigDecimal fee, BigDecimal proceeds,
                                         BigDecimal realizedProfit) {
    return of(TransactionKind.SELL, order).shares(shares.negate()).fee(fee).amount(BigDecimal.ZERO).proceeds(proceeds)
      .realizedProfit(realizedProfit).build();
  }

  // 받은 날짜에 현금에서 낸 양도세(수량 · 값 · 수수료 0)
  public static StrategyTransaction taxPayment(LocalDate date, BigDecimal fxRate, BigDecimal taxPaid,
                                               LocalDate defaultFxDate) {
    return of(TransactionKind.TAX_PAYMENT,
      new Order(null, null, date, date, BigDecimal.ZERO, fxRate, defaultFxDate,
        TransactionTrigger.CAPITAL_GAINS_TAX_PAYMENT))
      .shares(BigDecimal.ZERO).amount(BigDecimal.ZERO).fee(BigDecimal.ZERO).taxPaid(taxPaid).build();
  }

  // 받은 배당으로 더 산 거래(수수료 없음, 쓴 배당(USD)을 남기지 않으면 null)
  public static StrategyTransaction reinvestment(Order order, BigDecimal shares, BigDecimal cashUsed) {
    return of(TransactionKind.DIVIDEND_REINVESTMENT, order).shares(shares).amount(BigDecimal.ZERO).cashUsed(cashUsed)
      .build();
  }

  // 연말 정리로 판 거래(수량은 음수)
  public static StrategyTransaction harvestSale(Order order, BigDecimal shares, BigDecimal fee, BigDecimal proceeds,
                                                BigDecimal realizedProfit) {
    return of(TransactionKind.HARVEST_SELL, order).shares(shares.negate()).fee(fee).amount(BigDecimal.ZERO)
      .proceeds(proceeds).realizedProfit(realizedProfit).build();
  }

  // 연말 정리로 판 돈 전부로 다시 산 거래
  public static StrategyTransaction harvestBuy(Order order, BigDecimal shares, BigDecimal fee, BigDecimal cashUsed) {
    return of(TransactionKind.HARVEST_BUY, order).shares(shares).fee(fee).amount(BigDecimal.ZERO).cashUsed(cashUsed)
      .build();
  }

  private static StrategyTransactionBuilder of(TransactionKind kind, Order order) {
    return builder()
      .kind(kind)
      .symbol(order.symbol())
      .reason(order.reason())
      .date(order.date())
      .actualDate(order.actualDate())
      .price(order.price())
      .fxRate(order.fxRate())
      .defaultFxDate(order.defaultFxDate())
      .trigger(order.trigger());
  }

  // 낸 양도세만 바꾼 사본
  public StrategyTransaction withTaxPaid(BigDecimal paid) {
    return toBuilder().taxPaid(paid).build();
  }

  // 거래 이유만 바꾼 사본
  public StrategyTransaction withReason(TradeReason newReason) {
    return toBuilder().reason(newReason).build();
  }

  // 종목 · 이유를 실은 사본
  public StrategyTransaction labeled(String newSymbol, TradeReason newReason) {
    return toBuilder().symbol(newSymbol).reason(newReason).build();
  }

  // 기본 환율 날만 바꾼 사본
  public StrategyTransaction withDefaultFxDate(LocalDate date) {
    return toBuilder().defaultFxDate(date).build();
  }

  public TransactionKind kind() {
    return kind;
  }

  // 배당 대상 판정 · 매매 통계 순서 날짜(주말에 계획해 월요일에 샀으면 월요일)
  public LocalDate tradedOn() {
    return actualDate != null ? actualDate : date;
  }

  // 매도 거래(연말 정리 매도 포함)
  @JsonIgnore
  public boolean isSell() {
    return kind().sells();
  }

  // 양도세 납부 줄(수량 0, 사고팔기 아님)
  @JsonIgnore
  public boolean isTaxPayment() {
    return kind() == TransactionKind.TAX_PAYMENT;
  }

  // 연말 정리 매도 · 재매수 줄
  @JsonIgnore
  public boolean isYearEndHarvest() {
    return kind().harvests();
  }

  // 배당 재투자 줄
  @JsonIgnore
  public boolean isReinvestment() {
    return kind() == TransactionKind.DIVIDEND_REINVESTMENT;
  }

  // 거래일 계좌 현금 변화(USD, 매도로 받은 돈 - 매수에 쓴 현금 - 낸 양도세)
  @JsonIgnore
  public BigDecimal cashChange() {
    BigDecimal in = proceeds != null ? proceeds : BigDecimal.ZERO;
    BigDecimal out = cashUsed != null ? cashUsed : BigDecimal.ZERO;
    return in.subtract(out).subtract(taxPaid != null ? taxPaid : BigDecimal.ZERO);
  }

  // 매수 원가(USD, 넣은 원화를 산 날 환율로 바꾼 값 + 쓴 현금, 수수료 포함)
  @JsonIgnore
  public BigDecimal costUsd() {
    BigDecimal deposit = amount != null && amount.signum() > 0
      ? amount.divide(fxRate, 8, RoundingMode.HALF_UP)
      : BigDecimal.ZERO;
    return cashUsed != null ? deposit.add(cashUsed) : deposit;
  }
}
