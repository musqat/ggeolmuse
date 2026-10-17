package com.muscat.trade.infra.client;

import com.muscat.trade.common.enums.responses.TradeResponse;
import com.muscat.trade.common.exception.TradeException;
import com.muscat.trade.infra.client.dto.AccountBalanceDto;
import feign.FeignException;
import io.github.resilience4j.circuitbreaker.annotation.CircuitBreaker;
import io.github.resilience4j.retry.annotation.Retry;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;

// Resilience4j 패턴 적용 Wrapper(서킷브레이커 · 재시도)
@Slf4j
@Service
@RequiredArgsConstructor
public class UserServiceClientWrapper {

  private final UserServiceClient userServiceClient;

  @CircuitBreaker(name = "userService", fallbackMethod = "getAccountBalanceFallback")
  @Retry(name = "userService")
  public AccountBalanceDto getAccountBalance(Long accountId) {
    log.debug("Calling user-service getAccountBalance for accountId: {}", accountId);
    return userServiceClient.getAccountBalance(accountId);
  }

  // Fallback 메서드
  private AccountBalanceDto getAccountBalanceFallback(Long accountId, Exception ex) {
    // 4xx 는 장애가 아니다. 계좌 없음 · 접근 거부를 서비스 불가로 바꾸지 않고 그대로 올린다
    if (ex instanceof FeignException.FeignClientException clientError) {
      throw clientError;
    }
    log.error("계좌 잔액 조회 실패(대체 동작): accountId={}, error={}", accountId, ex.getMessage());
    throw new TradeException(TradeResponse.USER_SERVICE_ERROR, ex);
  }
}
