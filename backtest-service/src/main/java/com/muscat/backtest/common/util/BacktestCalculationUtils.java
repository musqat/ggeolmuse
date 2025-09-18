package com.muscat.backtest.common.util;

import com.muscat.backtest.common.enums.BacktestResponse;
import com.muscat.backtest.common.exception.BacktestException;
import com.muscat.backtest.infra.client.dto.DividendHistoryDto;
import com.muscat.commonlib.constants.CommonConstants;
import java.math.BigDecimal;
import java.math.RoundingMode;
import java.time.LocalDate;
import java.util.List;
import java.util.stream.Collectors;

// 백테스팅 계산 관련 유틸리티 클래스
public final class BacktestCalculationUtils {

  private BacktestCalculationUtils() {
    throw new UnsupportedOperationException("이 클래스는 인스턴스를 생성할 수 없습니다");
  }

  // 스케일 상수
  private static final int USD_SCALE = CommonConstants.USD_SCALE;
  private static final RoundingMode HALF_UP = CommonConstants.DEFAULT_ROUNDING_MODE;
  public static final int SHARES_SCALE = 6;
  public static final int PERCENT_PRECISION = 4;


  // 매수 가능한 주식 수량을 계산합니다
  public static BigDecimal calculateShares(BigDecimal investmentAmount, BigDecimal fxRate,
      BigDecimal stockPrice) {
    if (investmentAmount == null || fxRate == null || stockPrice == null) {
      throw new BacktestException(BacktestResponse.SHARES_CALCULATION_ERROR,
          "주식 수량 계산을 위한 투자금액, 환율, 주식가격은 null이 될 수 없습니다");
    }
    if (fxRate.compareTo(BigDecimal.ZERO) == 0) {
      throw new BacktestException(BacktestResponse.FX_CONVERSION_ERROR, "환율은 0이 될 수 없습니다");
    }
    if (stockPrice.compareTo(BigDecimal.ZERO) == 0) {
      throw new BacktestException(BacktestResponse.SHARES_CALCULATION_ERROR,
          "주식가격은 0이 될 수 없습니다");
    }

    return investmentAmount
        .divide(fxRate, 8, HALF_UP)
        .divide(stockPrice, 8, HALF_UP);
  }

  // 평균 가격을 계산합니다
  public static BigDecimal calculateAveragePrice(BigDecimal totalInvestment,
      BigDecimal totalFxRateSum, BigDecimal totalShares) {
    if (totalInvestment == null || totalFxRateSum == null || totalShares == null) {
      throw new IllegalArgumentException("모든 매개변수는 null이 될 수 없습니다");
    }
    if (totalFxRateSum.compareTo(BigDecimal.ZERO) == 0
        || totalShares.compareTo(BigDecimal.ZERO) == 0) {
      throw new IllegalArgumentException("환율 합계와 총 주식 수량은 0이 될 수 없습니다");
    }

    return totalInvestment
        .divide(totalFxRateSum, 8, HALF_UP)
        .divide(totalShares, 8, HALF_UP);
  }

  // 평균 환율을 계산합니다
  public static BigDecimal calculateAverageFxRate(BigDecimal totalFxRateSum, int transactionCount) {
    if (totalFxRateSum == null) {
      throw new IllegalArgumentException("환율 합계는 null이 될 수 없습니다");
    }
    if (transactionCount <= 0) {
      throw new IllegalArgumentException("거래 횟수는 0보다 커야 합니다");
    }

    return totalFxRateSum.divide(BigDecimal.valueOf(transactionCount), USD_SCALE, HALF_UP);
  }

  // 리스트의 중간값을 계산합니다
  public static BigDecimal calculateMedian(List<BigDecimal> values) {
    if (values == null || values.isEmpty()) {
      throw new IllegalArgumentException("값 리스트는 null이거나 비어있을 수 없습니다");
    }

    List<BigDecimal> sorted = values.stream()
        .sorted()
        .collect(Collectors.toList());

    int size = sorted.size();
    if (size % 2 == 0) {
      return sorted.get(size / 2 - 1)
          .add(sorted.get(size / 2))
          .divide(BigDecimal.valueOf(2), USD_SCALE, HALF_UP);
    } else {
      return sorted.get(size / 2);
    }
  }

  // BigDecimal 값을 지정된 스케일로 반올림합니다
  public static BigDecimal scaleValue(BigDecimal value, int scale) {
    if (value == null) {
      throw new IllegalArgumentException("값은 null이 될 수 없습니다");
    }

    return value.setScale(scale, HALF_UP);
  }


  // 주식 수량을 표준 스케일(6)로 반올림합니다
  public static BigDecimal scaleShares(BigDecimal shares) {
    return scaleValue(shares, SHARES_SCALE);
  }

  // 총 투자 대비 평균 수익률을 계산합니다
  public static BigDecimal calculateAverageReturn(List<BigDecimal> returnValues) {
    if (returnValues == null || returnValues.isEmpty()) {
      throw new IllegalArgumentException("수익률 리스트는 null이거나 비어있을 수 없습니다");
    }

    return returnValues.stream()
        .reduce(BigDecimal.ZERO, BigDecimal::add)
        .divide(BigDecimal.valueOf(returnValues.size()), USD_SCALE, HALF_UP);
  }

  // 투자 분할 금액을 계산합니다 (총 투자금액을 분할 횟수로 나눔)
  public static BigDecimal calculateInvestmentPerDivision(BigDecimal totalInvestment,
      int divisions) {
    if (totalInvestment == null) {
      throw new IllegalArgumentException("총 투자금액은 null이 될 수 없습니다");
    }
    if (divisions <= 0) {
      throw new IllegalArgumentException("분할 횟수는 0보다 커야 합니다");
    }

    return totalInvestment.divide(BigDecimal.valueOf(divisions), USD_SCALE, HALF_UP);
  }

  // 특정 기간 동안의 총 배당금을 계산합니다 (USD)
  public static BigDecimal calculateTotalDividends(DividendHistoryDto dividendHistory,
      BigDecimal shares,
      LocalDate startDate, LocalDate endDate) {
    if (dividendHistory == null || shares == null || startDate == null || endDate == null) {
      return BigDecimal.ZERO;
    }

    if (dividendHistory.getDividends() == null || dividendHistory.getDividends().isEmpty()) {
      return BigDecimal.ZERO;
    }

    return dividendHistory.getDividends().stream()
        .filter(dividend -> dividend.getExDate() != null)
        .filter(dividend -> !dividend.getExDate().isBefore(startDate) && !dividend.getExDate()
            .isAfter(endDate))
        .map(dividend -> dividend.getAmount() != null ? dividend.getAmount().multiply(shares)
            : BigDecimal.ZERO)
        .reduce(BigDecimal.ZERO, BigDecimal::add)
        .setScale(USD_SCALE, HALF_UP);
  }

  // 연간 배당 수익률을 계산합니다 (%)
  public static BigDecimal calculateDividendYield(BigDecimal totalDividends, BigDecimal shares,
      BigDecimal currentPrice) {
    if (totalDividends == null || shares == null || currentPrice == null ||
        shares.compareTo(BigDecimal.ZERO) == 0 || currentPrice.compareTo(BigDecimal.ZERO) == 0) {
      return BigDecimal.ZERO;
    }

    BigDecimal currentValue = shares.multiply(currentPrice);
    return totalDividends.divide(currentValue, PERCENT_PRECISION, HALF_UP)
        .multiply(BigDecimal.valueOf(100))
        .setScale(USD_SCALE, HALF_UP);
  }

  // 수수료를 고려한 정수 주식수 계산 (소수점 버림)
  public static BigDecimal calculateWholeSharesWithFee(BigDecimal usdAmount, BigDecimal stockPrice,
      BigDecimal feeRate) {
    if (usdAmount == null || stockPrice == null || feeRate == null) {
      throw new BacktestException(BacktestResponse.SHARES_CALCULATION_ERROR,
          "수수료 계산을 위한 파라미터는 null이 될 수 없습니다");
    }
    if (stockPrice.compareTo(BigDecimal.ZERO) == 0) {
      throw new BacktestException(BacktestResponse.SHARES_CALCULATION_ERROR,
          "주식가격은 0이 될 수 없습니다");
    }

    // 수수료 계산
    BigDecimal feeAmount = usdAmount.multiply(feeRate).setScale(USD_SCALE, HALF_UP);
    BigDecimal netAmount = usdAmount.subtract(feeAmount);

    // 음수 방지
    if (netAmount.compareTo(BigDecimal.ZERO) <= 0) {
      return BigDecimal.ZERO;
    }

    // 정수 주식수만 계산 (소수점 버림)
    return netAmount.divide(stockPrice, 0, RoundingMode.DOWN);
  }

  // 매매수수료 계산 (USD)
  public static BigDecimal calculateTradingFee(BigDecimal usdAmount, BigDecimal feeRate) {
    if (usdAmount == null || feeRate == null) {
      return BigDecimal.ZERO;
    }
    return usdAmount.multiply(feeRate).setScale(USD_SCALE, HALF_UP);
  }

  // 실제 사용된 총 비용 계산 (주식구매비 + 수수료)
  public static BigDecimal calculateTotalCost(BigDecimal shares, BigDecimal stockPrice,
      BigDecimal tradingFee) {
    if (shares == null || stockPrice == null) {
      throw new BacktestException(BacktestResponse.TRADING_FEE_CALCULATION_ERROR,
          "비용 계산을 위한 파라미터는 null이 될 수 없습니다");
    }

    BigDecimal stockCost = shares.multiply(stockPrice);
    BigDecimal fee = tradingFee != null ? tradingFee : BigDecimal.ZERO;
    return stockCost.add(fee).setScale(USD_SCALE, HALF_UP);
  }

  // 매수 후 잔액 계산 (USD)
  public static BigDecimal calculateRemainingCash(BigDecimal initialAmount, BigDecimal totalCost) {
    if (initialAmount == null || totalCost == null) {
      throw new BacktestException(BacktestResponse.REMAINING_CASH_CALCULATION_ERROR,
          "잔액 계산을 위한 파라미터는 null이 될 수 없습니다");
    }

    return initialAmount.subtract(totalCost).setScale(USD_SCALE, HALF_UP);
  }
}