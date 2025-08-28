package com.muscat.user.domain.account.dto.response;

import com.muscat.user.common.util.AccountCalculatorUtil;
import com.muscat.user.domain.account.entity.Account;
import java.math.BigDecimal;
import lombok.Builder;
import lombok.Getter;

@Getter
@Builder
public class BalanceResponseDto {

  // 계좌 정보
  private String accountName;
  private String accountNumber;

  // 잔액 정보
  private BigDecimal balanceUsd;          // USD 잔액
  private BigDecimal balanceKrw;          // KRW 잔액
  private BigDecimal currentValueKrw;     // 현재 환율 기준 총 자산(KRW)

  // 환율 정보
  private BigDecimal myAvgExchangeRate;    // 내 평균 환율
  private BigDecimal currentExchangeRate;  // 현재 시장 환율

  // 설정 정보
  private BigDecimal commissionRate;       // 수수료율
  private BigDecimal slippageRate;         // 슬리피지율

  public static BalanceResponseDto from(Account account, BigDecimal currentRate, AccountCalculatorUtil calculator) {
    return BalanceResponseDto.builder()
        .accountName(account.getAccountName())
        .accountNumber(account.getAccountNumber())
        .balanceKrw(account.getBalanceKrw())
        .balanceUsd(account.getBalanceUsd())
        .currentValueKrw(calculator.calculateTotalValueInKrw(account, currentRate))
        .myAvgExchangeRate(account.getAvgExchangeRate())
        .currentExchangeRate(currentRate)
        .commissionRate(account.getCommissionRate())
        .slippageRate(account.getSlippageRate())
        .build();
  }
}