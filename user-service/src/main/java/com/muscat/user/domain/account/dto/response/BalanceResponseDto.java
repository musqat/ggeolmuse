package com.muscat.user.domain.account.dto.response;

import com.muscat.user.common.util.AccountCalculatorUtil;
import com.muscat.user.domain.account.entity.Account;
import io.swagger.v3.oas.annotations.media.Schema;
import java.math.BigDecimal;
import lombok.Builder;
import lombok.Getter;

@Schema(description = "잔액 정보")
@Getter
@Builder
public class BalanceResponseDto {

  // 계좌 정보
  @Schema(description = "계좌명", example = "투자계좌1")
  private String accountName;

  @Schema(description = "계좌번호", example = "ACC_20240918_001")
  private String accountNumber;

  // 잔액 정보
  @Schema(description = "USD 잔액", example = "500.00")
  private BigDecimal balanceUsd;          // USD 잔액

  @Schema(description = "KRW 잔액", example = "1000000.00")
  private BigDecimal balanceKrw;          // KRW 잔액

  @Schema(description = "현재 환율 기준 총 자산(KRW)", example = "1675000.00")
  private BigDecimal currentValueKrw;     // 현재 환율 기준 총 자산(KRW)

  // 환율 정보
  @Schema(description = "내 평균 환율", example = "1350.50")
  private BigDecimal myAvgExchangeRate;    // 내 평균 환율

  @Schema(description = "현재 시장 환율", example = "1355.00")
  private BigDecimal currentExchangeRate;  // 현재 시장 환율

  // 설정 정보
  @Schema(description = "수수료율", example = "0.002")
  private BigDecimal commissionRate;       // 수수료율

  @Schema(description = "슬리피지율", example = "0.001")
  private BigDecimal slippageRate;         // 슬리피지율

  public static BalanceResponseDto from(Account account, BigDecimal currentRate,
      AccountCalculatorUtil calculator) {
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