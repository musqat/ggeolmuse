package com.muscat.trade.common.util;

import com.muscat.trade.common.constants.TradeConstants;
import com.muscat.trade.common.enums.responses.TradeResponse;
import com.muscat.trade.common.exception.TradeException;
import com.muscat.trade.common.logging.TradeLogger;
import com.muscat.trade.config.TradeProperties;
import com.muscat.trade.infra.client.UserServiceClientWrapper;
import com.muscat.trade.infra.client.dto.AccountBalanceDto;
import feign.FeignException;
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
    AccountBalanceDto response;
    try {
      response = userServiceClientWrapper.getAccountBalance(Long.valueOf(accountId));
    } catch (FeignException.NotFound e) {
      // 사용자 서비스는 남의 계좌도 404 로 준다. 서비스 오류가 아니라 계좌 없음이다
      throw new TradeException(TradeResponse.ACCOUNT_NOT_FOUND);
    } catch (FeignException.Forbidden e) {
      throw new TradeException(TradeResponse.ACCOUNT_ACCESS_DENIED);
    } catch (TradeException e) {
      // 대체 동작이 이미 사용자 서비스 오류로 바꿔 올린 것이다
      throw e;
    } catch (Exception e) {
      log.error("계좌 정보 조회 실패: accountId={}", accountId, e);
      throw new TradeException(TradeResponse.USER_SERVICE_ERROR);
    }
    // 빈 응답은 호출 실패가 아니라 계좌 없음이다. try 안에서 던지면 아래 catch 가 서비스 오류로 바꿔 버린다
    if (response == null) {
      throw new TradeException(TradeResponse.ACCOUNT_NOT_FOUND);
    }
    return response;
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
