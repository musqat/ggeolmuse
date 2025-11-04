package com.muscat.trade.infra.client;

import com.muscat.trade.infra.client.dto.AccountBalanceDto;
import org.springframework.cloud.openfeign.FeignClient;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestParam;

import java.math.BigDecimal;

@FeignClient(name = "user-service", url = "http://user-service:8080")
public interface UserServiceClient {

  @GetMapping("/api/accounts/{accountId}/balance")
  AccountBalanceDto getAccountBalance(
      @PathVariable("accountId") Long accountId
  );

  @PostMapping("/api/internal/account/{accountId}/trade/balance")
  Void updateTradeBalance(
      @PathVariable("accountId") Long accountId,
      @RequestParam("usdAmount") BigDecimal usdAmount,
      @RequestParam("tradeType") String tradeType,
      @RequestParam("description") String description
  );

  @PostMapping("/api/internal/account/{accountId}/dividend/balance")
  Void updateDividendBalance(
      @PathVariable("userId") String userId,
      @PathVariable("accountId") Long accountId,
      @RequestParam("amount") BigDecimal amount
  );
}