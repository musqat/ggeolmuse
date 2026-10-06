package com.muscat.trade.common.util;

import com.muscat.trade.common.constants.TradeConstants;
import com.muscat.trade.common.enums.responses.TradeResponse;
import com.muscat.trade.common.exception.TradeException;
import com.muscat.trade.common.logging.TradeLogger;
import com.muscat.trade.config.TradeProperties;
import com.muscat.trade.infra.client.UserServiceClientWrapper;
import com.muscat.trade.infra.client.dto.AccountBalanceDto;
import java.math.BigDecimal;
import java.math.RoundingMode;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Component;

@Component
@RequiredArgsConstructor
@Slf4j
public class TradeUtils {

  private final UserServiceClientWrapper userServiceClientWrapper;
  private final TradeLogger tradeLogger;
  private final TradeProperties tradeProperties;

  // 거래 수수료 계산. 비율이 0 이면 수수료도 0 이고, 비율이 없거나 음수일 때만 기본 비율을 쓴다
  public BigDecimal calculateFee(AccountBalanceDto accountBalance, BigDecimal tradeAmount) {
    BigDecimal commissionRate = accountBalance.getCommissionRate();

    if (commissionRate != null && commissionRate.signum() >= 0) {
      BigDecimal fee = tradeAmount.multiply(commissionRate)
        .setScale(tradeProperties.getCalculation().getPricePrecision(), RoundingMode.HALF_UP);

      log.debug("수수료 계산: 거래금액={}, 수수료율={}%, 수수료={}",
        tradeAmount, commissionRate.multiply(TradeConstants.PERCENTAGE_MULTIPLIER), fee);
      return fee;
    }

    // 기본 수수료율 사용
    log.debug("기본 수수료율 사용: 거래금액={}, 기본수수료율={}%",
      tradeAmount,
      tradeProperties.getFee().getDefaultRate().multiply(TradeConstants.PERCENTAGE_MULTIPLIER));
    return tradeAmount.multiply(tradeProperties.getFee().getDefaultRate())
      .setScale(tradeProperties.getCalculation().getPricePrecision(), RoundingMode.HALF_UP);
  }

  // 계좌 잔액 조회
  public AccountBalanceDto getAccountBalance(String accountId) {
    try {
      var response = userServiceClientWrapper.getAccountBalance(Long.valueOf(accountId));
      if (response == null) {
        throw new TradeException(TradeResponse.ACCOUNT_NOT_FOUND);
      }
      return response;
    } catch (Exception e) {
      log.error("계좌 정보 조회 실패: accountId={}", accountId, e);
      throw new TradeException(TradeResponse.USER_SERVICE_ERROR);
    }
  }

  // 매수 잔액 검증
  public void validateBuyBalance(String userId, String accountId,
    BigDecimal totalAmount, AccountBalanceDto accountBalance) {
    if (accountBalance.getBalanceUsd().compareTo(totalAmount) < 0) {
      tradeLogger.logBalanceCheck(userId, accountId, totalAmount,
        accountBalance.getBalanceUsd(), false);
      throw new TradeException(TradeResponse.INSUFFICIENT_BALANCE);
    }
    tradeLogger.logBalanceCheck(userId, accountId, totalAmount,
      accountBalance.getBalanceUsd(), true);
  }
}
