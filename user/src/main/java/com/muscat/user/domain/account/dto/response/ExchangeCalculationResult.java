package com.muscat.user.domain.account.dto.response;

import com.muscat.user.common.util.MoneyUtils;

import lombok.Getter;
import lombok.RequiredArgsConstructor;
import lombok.ToString;

import java.math.BigDecimal;

@Getter
@RequiredArgsConstructor
@ToString
public class ExchangeCalculationResult {

  // 입력값들
  private final BigDecimal requestAmount;      // 요청 금액 (명확한 네이밍)
  private final BigDecimal exchangeRate;       // 적용 환율
  private final String fromCurrency;          // 원본 통화
  private final String toCurrency;            // 대상 통화

  // 계산 결과들
  private final BigDecimal beforeCommissionAmount;  // 수수료 차감 전 환전 금액
  private final BigDecimal commissionAmount;        // 수수료 금액
  private final BigDecimal finalAmount;             // 최종 받을 금액 (수수료 차감 후)

  /**
   *  KRW → USD 환전 결과 생성
   */
  public static ExchangeCalculationResult ofKrwToUsd(
      BigDecimal krwAmount, BigDecimal exchangeRate, BigDecimal commissionRate) {

    BigDecimal usdAmount = MoneyUtils.calculateKrwToUsd(krwAmount, exchangeRate);

    return new ExchangeCalculationResult(
        krwAmount, exchangeRate, "KRW", "USD",
        usdAmount, BigDecimal.ZERO, usdAmount
    );
  }

  /**
   * USD → KRW 환전 결과 생성
   */
  public static ExchangeCalculationResult ofUsdToKrw(
      BigDecimal usdAmount, BigDecimal exchangeRate, BigDecimal commissionRate) {

    // 수수료 없는 환전
    BigDecimal krwAmount = MoneyUtils.calculateUsdToKrw(usdAmount, exchangeRate);

    return new ExchangeCalculationResult(
        usdAmount, exchangeRate, "USD", "KRW",
        krwAmount, BigDecimal.ZERO, krwAmount
    );
  }

  // ========== 계산 ========== //

  /**
   * KRW → USD 환전 시 실제 차감될 총 KRW 금액
   * (요청 금액만 차감, 수수료는 이미 포함됨)
   */
  public BigDecimal getTotalKrwDeduction() {
    if (!"KRW".equals(fromCurrency)) {
      return BigDecimal.ZERO;
    }
    return requestAmount;
  }

  /**
   * USD → KRW 환전 시 실제 받을 KRW 금액
   * (환전 금액 - 수수료)
   */
  public BigDecimal getNetKrwAmount() {
    if (!"KRW".equals(toCurrency)) {
      return BigDecimal.ZERO;
    }
    return finalAmount;
  }

  /**
   * 수수료율 계산
   */
  public BigDecimal getCommissionRate() {
    if (commissionAmount.compareTo(BigDecimal.ZERO) == 0) {
      return BigDecimal.ZERO;
    }

    BigDecimal baseAmount = "KRW".equals(fromCurrency) ? requestAmount : beforeCommissionAmount;
    return commissionAmount.divide(baseAmount, 6, MoneyUtils.DEFAULT_ROUNDING);
  }

  // ========== 유틸리티 메서드들 ========== //

  /**
   * 환전 요약 정보
   */
  public String getSummary() {
    return String.format("%s %s → %s %s (수수료: %s)",
        MoneyUtils.formatAmount(requestAmount, fromCurrency), fromCurrency,
        MoneyUtils.formatAmount(finalAmount, toCurrency), toCurrency,
        MoneyUtils.formatAmount(commissionAmount, getCommissionCurrency()));
  }

  /**
   * 상세 정보 (환율 포함)
   */
  public String getDetailedSummary() {
    return String.format("환전: %s %s → %s %s | 환율: %s | 수수료: %s %s (%.3f%%)",
        MoneyUtils.formatAmount(requestAmount, fromCurrency), fromCurrency,
        MoneyUtils.formatAmount(finalAmount, toCurrency), toCurrency,
        exchangeRate,
        MoneyUtils.formatAmount(commissionAmount, getCommissionCurrency()), getCommissionCurrency(),
        getCommissionRate().multiply(new BigDecimal("100")));
  }

  /**
   * 수수료가 청구되는 통화
   * 현재 정책: 항상 KRW
   */
  public String getCommissionCurrency() {
    return "KRW";
  }


}