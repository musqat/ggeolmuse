package com.muscat.user.common.util;

import com.muscat.user.common.exceptions.AccountException;
import com.muscat.user.common.responses.AccountResponse;
import lombok.extern.slf4j.Slf4j;

import java.math.BigDecimal;
import java.math.RoundingMode;

// 금융 계산 및 통화 처리 유틸리티
@Slf4j
public final class MoneyUtils {

  // 통화별 정밀도 정책
  public static final int KRW_SCALE = 0;
  public static final int USD_SCALE = 2;
  public static final int EXCHANGE_RATE_SCALE = 6;

  // 반올림 정책
  public static final RoundingMode DEFAULT_ROUNDING = RoundingMode.HALF_UP;
  public static final RoundingMode DIVISION_ROUNDING = RoundingMode.HALF_UP;

  // 계산 시 중간값 정밀도
  private static final int CALCULATION_SCALE = 10;

  // 금액 제한
  public static final BigDecimal MIN_EXCHANGE_AMOUNT_KRW = new BigDecimal("1000");
  public static final BigDecimal MIN_EXCHANGE_AMOUNT_USD = new BigDecimal("1.00");
  public static final BigDecimal MAX_SINGLE_EXCHANGE_KRW = new BigDecimal("50000000");

  private MoneyUtils() {
    throw new AssertionError("Utility class cannot be instantiated");
  }

  // ========== 통화별 반올림 ========== //

  // KRW 금액을 정책에 맞게 반올림 (소수점 제거)
  public static BigDecimal roundKrw(BigDecimal amount) {
    if (amount == null) {
      return BigDecimal.ZERO;
    }
    return amount.setScale(KRW_SCALE, DEFAULT_ROUNDING);
  }

  // USD 금액을 정책에 맞게 반올림 (센트 단위)
  public static BigDecimal roundUsd(BigDecimal amount) {
    if (amount == null) {
      return BigDecimal.ZERO;
    }
    return amount.setScale(USD_SCALE, DEFAULT_ROUNDING);
  }

  // 환율을 정책에 맞게 반올림
  public static BigDecimal roundExchangeRate(BigDecimal rate) {
    if (rate == null) {
      return BigDecimal.ZERO;
    }
    return rate.setScale(EXCHANGE_RATE_SCALE, DEFAULT_ROUNDING);
  }

  // ========== 환전 계산 로직 ========== //

  // KRW → USD 환전 계산
  public static BigDecimal calculateKrwToUsd(BigDecimal krwAmount, BigDecimal exchangeRate) {
    validateExchangeInputs(krwAmount, exchangeRate, "KRW", "USD");

    // 높은 정밀도로 계산 후 USD 정책으로 반올림
    BigDecimal usdAmount = krwAmount.divide(exchangeRate, CALCULATION_SCALE, DIVISION_ROUNDING);
    BigDecimal result = roundUsd(usdAmount);

    log.debug("KRW→USD 환전: {}원 ÷ {} = {}달러", krwAmount, exchangeRate, result);
    return result;
  }

  // USD → KRW 환전 계산
  public static BigDecimal calculateUsdToKrw(BigDecimal usdAmount, BigDecimal exchangeRate) {
    validateExchangeInputs(usdAmount, exchangeRate, "USD", "KRW");

    // 곱셈 후 KRW 정책으로 반올림
    BigDecimal krwAmount = usdAmount.multiply(exchangeRate);
    BigDecimal result = roundKrw(krwAmount);

    log.debug("USD→KRW 환전: {}달러 × {} = {}원", usdAmount, exchangeRate, result);
    return result;
  }


  // ========== 금액 검증 로직 ========== //

  // 양수 금액 검증
  public static void validatePositiveAmount(BigDecimal amount, String fieldName) {
    if (amount == null) {
      throw new AccountException(AccountResponse.INVALID_DEPOSIT_AMOUNT,
          fieldName + "이(가) null입니다");
    }
    if (amount.compareTo(BigDecimal.ZERO) <= 0) {
      throw new AccountException(AccountResponse.INVALID_DEPOSIT_AMOUNT,
          fieldName + "은(는) 0보다 커야 합니다: " + amount);
    }
  }

  // 환전 최소 금액 검증
  public static void validateMinimumExchangeAmount(BigDecimal amount, String currency) {
    validatePositiveAmount(amount, "환전 금액");

    BigDecimal minimumAmount = switch (currency.toUpperCase()) {
      case "KRW" -> MIN_EXCHANGE_AMOUNT_KRW;
      case "USD" -> MIN_EXCHANGE_AMOUNT_USD;
      default -> throw new AccountException(AccountResponse.INVALID_CURRENCY);
    };

    if (amount.compareTo(minimumAmount) < 0) {
      throw new AccountException(AccountResponse.INVALID_DEPOSIT_AMOUNT,
          String.format("최소 환전 금액은 %s %s입니다", minimumAmount, currency));
    }
  }

  // 환전 최대 금액 검증 (KRW 기준)
  public static void validateMaximumExchangeAmount(BigDecimal krwAmount) {
    validatePositiveAmount(krwAmount, "환전 금액");

    if (krwAmount.compareTo(MAX_SINGLE_EXCHANGE_KRW) > 0) {
      throw new AccountException(AccountResponse.INVALID_DEPOSIT_AMOUNT,
          String.format("1회 최대 환전 금액은 %s원입니다", MAX_SINGLE_EXCHANGE_KRW));
    }
  }

  // 잔액 충분성 검증
  public static void validateSufficientBalance(BigDecimal currentBalance, BigDecimal requiredAmount, String currency) {
    validatePositiveAmount(requiredAmount, "필요 금액");

    if (currentBalance == null) {
      currentBalance = BigDecimal.ZERO;
    }

    if (currentBalance.compareTo(requiredAmount) < 0) {
      throw new AccountException(AccountResponse.INSUFFICIENT_BALANCE,
          String.format("잔액 부족: 현재 %s %s, 필요 %s %s",
              currentBalance, currency, requiredAmount, currency));
    }
  }

  // ========== 내부 검증 메서드들 ========== //

  private static void validateExchangeInputs(BigDecimal amount, BigDecimal exchangeRate, String fromCurrency, String toCurrency) {
    validatePositiveAmount(amount, fromCurrency + " 금액");

    if (exchangeRate == null || exchangeRate.compareTo(BigDecimal.ZERO) <= 0) {
      throw new AccountException(AccountResponse.INVALID_EXCHANGE_RATE,
          "환율은 0보다 커야 합니다: " + exchangeRate);
    }

    // 환율 합리성 체크 (USD/KRW 기준 500~2000 범위)
    if ("USD".equals(toCurrency) || "KRW".equals(toCurrency)) {
      BigDecimal minRate = new BigDecimal("500");
      BigDecimal maxRate = new BigDecimal("2000");

      if (exchangeRate.compareTo(minRate) < 0 || exchangeRate.compareTo(maxRate) > 0) {
        log.warn("비정상적인 환율 감지: {} (정상 범위: {}-{})", exchangeRate, minRate, maxRate);
      }
    }

    // 환전 최소 금액 체크
    validateMinimumExchangeAmount(amount, fromCurrency);

    // KRW 환전 시 최대 금액 체크
    if ("KRW".equals(fromCurrency)) {
      validateMaximumExchangeAmount(amount);
    }
  }


  // ========== 유틸리티 메서드들 ========== //

  // 두 금액이 같은지 비교 (통화별 정밀도 고려)
  public static boolean isEqual(BigDecimal amount1, BigDecimal amount2, String currency) {
    if (amount1 == null && amount2 == null) return true;
    if (amount1 == null || amount2 == null) return false;

    // 통화별 정밀도로 반올림 후 비교
    BigDecimal rounded1 = roundByCurrency(amount1, currency);
    BigDecimal rounded2 = roundByCurrency(amount2, currency);

    return rounded1.compareTo(rounded2) == 0;
  }

  // 금액을 통화에 맞게 포맷팅
  public static String formatAmount(BigDecimal amount, String currency) {
    if (amount == null) return "0";

    BigDecimal rounded = roundByCurrency(amount, currency);

    return switch (currency.toUpperCase()) {
      case "KRW" -> String.format("%,d원", rounded.longValue());
      case "USD" -> String.format("$%,.2f", rounded);
      default -> rounded.toString() + " " + currency;
    };
  }

  private static BigDecimal roundByCurrency(BigDecimal amount, String currency) {
    return switch (currency.toUpperCase()) {
      case "KRW" -> roundKrw(amount);
      case "USD" -> roundUsd(amount);
      default -> amount;
    };
  }
}