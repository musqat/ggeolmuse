package com.muscat.commonlib.util;

import com.muscat.commonlib.exception.BusinessException;
import com.muscat.commonlib.exception.CommonResponse;
import java.math.BigDecimal;
import java.math.RoundingMode;
import lombok.extern.slf4j.Slf4j;

@Slf4j
public final class MoneyUtils {

  public static final int KRW_SCALE = 0;
  public static final int USD_SCALE = 2;
  public static final int EXCHANGE_RATE_SCALE = 6;

  public static final RoundingMode ROUND_MODE = RoundingMode.HALF_UP;

  private static final int CALCULATION_SCALE = 10;

  public static final BigDecimal MIN_EXCHANGE_AMOUNT_KRW = new BigDecimal("1000");
  public static final BigDecimal MIN_EXCHANGE_AMOUNT_USD = new BigDecimal("1.00");
  public static final BigDecimal MAX_SINGLE_EXCHANGE_KRW = new BigDecimal("50000000");

  // USD/KRW 정상 범위(원)
  private static final BigDecimal MIN_EXCHANGE_RATE = new BigDecimal("500");
  private static final BigDecimal MAX_EXCHANGE_RATE = new BigDecimal("2000");

  private MoneyUtils() {
  }

  // KRW 금액 반올림 (소수점 제거)
  public static BigDecimal roundKrw(BigDecimal amount) {
    return amount == null ? BigDecimal.ZERO : amount.setScale(KRW_SCALE, ROUND_MODE);
  }

  // USD 금액 반올림 (센트 단위)
  public static BigDecimal roundUsd(BigDecimal amount) {
    return amount == null ? BigDecimal.ZERO : amount.setScale(USD_SCALE, ROUND_MODE);
  }

  // 환율 반올림
  public static BigDecimal roundExchangeRate(BigDecimal rate) {
    return rate == null ? BigDecimal.ZERO : rate.setScale(EXCHANGE_RATE_SCALE, ROUND_MODE);
  }

  // KRW → USD 환전 계산 (전체 검증)
  public static BigDecimal calculateKrwToUsd(BigDecimal krwAmount, BigDecimal exchangeRate) {
    validateExchangeInputs(krwAmount, exchangeRate, "KRW", "USD");
    return convertKrwToUsd(krwAmount, exchangeRate);
  }

  // KRW → USD 변환 (기본 검증만)
  public static BigDecimal convertKrwToUsd(BigDecimal krwAmount, BigDecimal exchangeRate) {
    validateBasicExchangeInputs(krwAmount, exchangeRate);
    return roundUsd(krwAmount.divide(exchangeRate, CALCULATION_SCALE, ROUND_MODE));
  }

  // USD → KRW 환전 계산 (전체 검증)
  public static BigDecimal calculateUsdToKrw(BigDecimal usdAmount, BigDecimal exchangeRate) {
    validateExchangeInputs(usdAmount, exchangeRate, "USD", "KRW");
    return convertUsdToKrw(usdAmount, exchangeRate);
  }

  // USD → KRW 변환 (기본 검증만)
  public static BigDecimal convertUsdToKrw(BigDecimal usdAmount, BigDecimal exchangeRate) {
    validateBasicExchangeInputs(usdAmount, exchangeRate);
    return roundKrw(usdAmount.multiply(exchangeRate));
  }

  // 양수 금액 검증
  public static void validatePositiveAmount(BigDecimal amount) {
    if (amount == null || amount.compareTo(BigDecimal.ZERO) <= 0) {
      throw new BusinessException(CommonResponse.INVALID_AMOUNT);
    }
  }

  // 환전 최소 금액 검증
  public static void validateMinimumExchangeAmount(BigDecimal amount, String currency) {
    validatePositiveAmount(amount);

    BigDecimal minimumAmount = switch (currency.toUpperCase()) {
      case "KRW" -> MIN_EXCHANGE_AMOUNT_KRW;
      case "USD" -> MIN_EXCHANGE_AMOUNT_USD;
      default -> throw new BusinessException(CommonResponse.INVALID_CURRENCY);
    };

    if (amount.compareTo(minimumAmount) < 0) {
      throw new BusinessException(CommonResponse.INSUFFICIENT_AMOUNT);
    }
  }

  // 환전 최대 금액 검증 (KRW 기준)
  public static void validateMaximumExchangeAmount(BigDecimal krwAmount) {
    validatePositiveAmount(krwAmount);

    if (krwAmount.compareTo(MAX_SINGLE_EXCHANGE_KRW) > 0) {
      throw new BusinessException(CommonResponse.EXCESSIVE_AMOUNT);
    }
  }

  // 잔액 충분성 검증
  public static void validateSufficientBalance(BigDecimal currentBalance, BigDecimal requiredAmount,
                                               String currency) {
    validatePositiveAmount(requiredAmount);

    if (currentBalance == null) {
      currentBalance = BigDecimal.ZERO;
    }

    if (currentBalance.compareTo(requiredAmount) < 0) {
      throw new BusinessException(CommonResponse.INSUFFICIENT_BALANCE);
    }
  }

  // 두 금액이 같은지 비교 (통화별 정밀도 고려)
  public static boolean isEqual(BigDecimal amount1, BigDecimal amount2, String currency) {
    if (amount1 == null && amount2 == null) {
      return true;
    }
    if (amount1 == null || amount2 == null) {
      return false;
    }

    BigDecimal rounded1 = "KRW".equals(currency) ? roundKrw(amount1) : roundUsd(amount1);
    BigDecimal rounded2 = "KRW".equals(currency) ? roundKrw(amount2) : roundUsd(amount2);

    return rounded1.compareTo(rounded2) == 0;
  }

  // 금액을 통화에 맞게 포맷팅
  public static String formatAmount(BigDecimal amount, String currency) {
    if (amount == null) {
      return "0";
    }

    return switch (currency.toUpperCase()) {
      case "KRW" -> String.format("%,d원", roundKrw(amount).longValue());
      case "USD" -> String.format("$%,.2f", roundUsd(amount));
      default -> amount.toString() + " " + currency;
    };
  }

  // 기본 산술 연산
  public static BigDecimal add(BigDecimal a, BigDecimal b) {
    if (a == null) {
      a = BigDecimal.ZERO;
    }
    if (b == null) {
      b = BigDecimal.ZERO;
    }
    return a.add(b);
  }

  public static BigDecimal subtract(BigDecimal a, BigDecimal b) {
    if (a == null) {
      a = BigDecimal.ZERO;
    }
    if (b == null) {
      b = BigDecimal.ZERO;
    }
    return a.subtract(b);
  }

  public static BigDecimal multiply(BigDecimal a, BigDecimal b) {
    if (a == null || b == null) {
      return BigDecimal.ZERO;
    }
    return a.multiply(b);
  }

  // 기본 환전 입력값 검증
  private static void validateBasicExchangeInputs(BigDecimal amount, BigDecimal exchangeRate) {
    validatePositiveAmount(amount);
    if (exchangeRate == null || exchangeRate.compareTo(BigDecimal.ZERO) <= 0) {
      throw new BusinessException(CommonResponse.INVALID_EXCHANGE_RATE);
    }
  }

  // 전체 환전 입력값 검증
  private static void validateExchangeInputs(BigDecimal amount, BigDecimal exchangeRate,
                                             String fromCurrency, String toCurrency) {
    validatePositiveAmount(amount);

    if (exchangeRate == null || exchangeRate.compareTo(BigDecimal.ZERO) <= 0) {
      throw new BusinessException(CommonResponse.INVALID_EXCHANGE_RATE);
    }

    if (("USD".equals(toCurrency) || "KRW".equals(toCurrency)) && !isExchangeRateInRange(exchangeRate)) {
      log.warn("비정상적인 환율 감지: {} (정상 범위: {}-{})", exchangeRate, MIN_EXCHANGE_RATE, MAX_EXCHANGE_RATE);
    }

    validateMinimumExchangeAmount(amount, fromCurrency);

    if ("KRW".equals(fromCurrency)) {
      validateMaximumExchangeAmount(amount);
    }
  }

  // 수익률 계산 (백분율) - (현재값 - 기준값) / 기준값 * 100
  public static BigDecimal calculateReturnRate(BigDecimal baseValue, BigDecimal currentValue) {
    if (baseValue == null || baseValue.compareTo(BigDecimal.ZERO) == 0) {
      throw new BusinessException(CommonResponse.INVALID_BASE_VALUE);
    }
    if (currentValue == null) {
      currentValue = BigDecimal.ZERO;
    }

    return currentValue.subtract(baseValue)
      .divide(baseValue, 4, ROUND_MODE)
      .multiply(BigDecimal.valueOf(100))
      .setScale(USD_SCALE, ROUND_MODE);
  }

  // 환율이 정상 범위(500 ~ 2000원) 안인지
  public static boolean isExchangeRateInRange(BigDecimal exchangeRate) {
    return exchangeRate != null
      && exchangeRate.compareTo(MIN_EXCHANGE_RATE) >= 0
      && exchangeRate.compareTo(MAX_EXCHANGE_RATE) <= 0;
  }

  // 환율 검증(0 이하거나 정상 범위 밖이면 예외)
  public static void validateExchangeRate(BigDecimal exchangeRate) {
    if (exchangeRate == null || exchangeRate.compareTo(BigDecimal.ZERO) <= 0) {
      throw new BusinessException(CommonResponse.INVALID_EXCHANGE_RATE);
    }
    if (!isExchangeRateInRange(exchangeRate)) {
      throw new BusinessException(CommonResponse.EXCHANGE_RATE_OUT_OF_RANGE);
    }
  }
}
