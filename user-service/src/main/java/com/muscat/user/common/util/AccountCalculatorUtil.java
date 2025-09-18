package com.muscat.user.common.util;

import com.muscat.commonlib.util.MoneyUtils;
import com.muscat.user.domain.account.dto.response.ExchangeCalculationResult;
import com.muscat.user.domain.account.entity.Account;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Component;

import java.math.BigDecimal;

// 계좌 관련 계산 로직을 담당하는 유틸리티
@Component
@Slf4j
public class AccountCalculatorUtil {

  // USD 잔액을 현재 환율로 KRW 환산
  public BigDecimal calculateUsdValueInKrw(Account account, BigDecimal currentExchangeRate) {
    if (account.getBalanceUsd() == null || currentExchangeRate == null) {
      return BigDecimal.ZERO;
    }

    if (MoneyUtils.isEqual(account.getBalanceUsd(), BigDecimal.ZERO, "USD")) {
      return BigDecimal.ZERO;
    }

    return MoneyUtils.calculateUsdToKrw(account.getBalanceUsd(), currentExchangeRate);
  }

  // 총 자산 가치 (KRW + USD 환산가치)
  public BigDecimal calculateTotalValueInKrw(Account account, BigDecimal currentExchangeRate) {
    BigDecimal krwValue = account.getBalanceKrw() != null ? account.getBalanceKrw() : BigDecimal.ZERO;
    BigDecimal usdValueInKrw = calculateUsdValueInKrw(account, currentExchangeRate);

    return MoneyUtils.roundKrw(krwValue.add(usdValueInKrw));
  }

  // 환전 요청 유효성 검증
  public void validateExchangeRequest(Account account, BigDecimal amount, String fromCurrency) {
    MoneyUtils.validatePositiveAmount(amount, fromCurrency + " 환전 금액");
    MoneyUtils.validateMinimumExchangeAmount(amount, fromCurrency);
    
    BigDecimal currentBalance = getBalanceByCurrency(account, fromCurrency);
    MoneyUtils.validateSufficientBalance(currentBalance, amount, fromCurrency);
    
    if ("KRW".equals(fromCurrency)) {
      MoneyUtils.validateMaximumExchangeAmount(amount);
    }
  }

  // 환전 계산
  public ExchangeCalculationResult calculateExchangeWithCommission(
      Account account, BigDecimal amount, String fromCurrency, String toCurrency,
      BigDecimal exchangeRate) {

    return switch (fromCurrency + "_TO_" + toCurrency) {
      case "KRW_TO_USD" -> ExchangeCalculationResult.ofKrwToUsd(amount, exchangeRate);
      case "USD_TO_KRW" -> ExchangeCalculationResult.ofUsdToKrw(amount, exchangeRate);
      default -> throw new IllegalArgumentException("지원하지 않는 환전: " + fromCurrency + " -> " + toCurrency);
    };
  }

  // 평균 환율 계산
  public BigDecimal calculateNewAverageRate(Account account, BigDecimal newKrwAmount, BigDecimal newExchangeRate) {
    BigDecimal currentTotalKrw = account.getTotalExchangedKrw() != null ?
        account.getTotalExchangedKrw() : BigDecimal.ZERO;
    BigDecimal currentUsdBalance = account.getBalanceUsd() != null ?
        account.getBalanceUsd() : BigDecimal.ZERO;

    BigDecimal newTotalKrw = currentTotalKrw.add(newKrwAmount);
    BigDecimal additionalUsd = MoneyUtils.calculateKrwToUsd(newKrwAmount, newExchangeRate);
    BigDecimal newTotalUsd = currentUsdBalance.add(additionalUsd);

    if (MoneyUtils.isEqual(newTotalUsd, BigDecimal.ZERO, "USD")) {
      return BigDecimal.ZERO;
    }

    BigDecimal avgRate = newTotalKrw.divide(newTotalUsd, 10, MoneyUtils.ROUND_MODE);
    return MoneyUtils.roundExchangeRate(avgRate);
  }

  // 통화별 잔액 조회
  public BigDecimal getBalanceByCurrency(Account account, String currency) {
    return switch (currency.toUpperCase()) {
      case "KRW" -> account.getBalanceKrw() != null ? account.getBalanceKrw() : BigDecimal.ZERO;
      case "USD" -> account.getBalanceUsd() != null ? account.getBalanceUsd() : BigDecimal.ZERO;
      default -> throw new IllegalArgumentException("지원하지 않는 통화: " + currency);
    };
  }

  
}