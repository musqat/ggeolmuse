package com.muscat.trade.infra.client;

import com.muscat.trade.infra.client.dto.AccountBalanceDto;
import io.github.resilience4j.circuitbreaker.annotation.CircuitBreaker;
import io.github.resilience4j.retry.annotation.Retry;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;

/**
 * Resilience4j 패턴 적용 Wrapper
 * Circuit Breaker: 연속된 실패 시 호출 차단
 * Retry: 일시적 실패 시 재시도
 */
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
    log.error("Fallback triggered for getAccountBalance. AccountId: {}, Error: {}",
        accountId, ex.getMessage());
    throw new RuntimeException("User service is currently unavailable. Please try again later.", ex);
  }
}
