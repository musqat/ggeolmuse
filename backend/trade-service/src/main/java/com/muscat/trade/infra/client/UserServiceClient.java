package com.muscat.trade.infra.client;

import com.muscat.trade.infra.client.dto.AccountBalanceDto;
import org.springframework.cloud.openfeign.FeignClient;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;

@FeignClient(name = "user-service", url = "http://user-service:8080")
public interface UserServiceClient {

  @GetMapping("/api/accounts/{accountId}/balance")
  AccountBalanceDto getAccountBalance(
      @PathVariable("accountId") Long accountId
  );
}