package com.muscat.trade.infra.client;

import com.muscat.trade.infra.client.dto.AccountBalanceDto;
import com.muscat.trade.infra.client.dto.UserApiResponse;
import org.springframework.cloud.openfeign.FeignClient;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestParam;

import java.math.BigDecimal;

@FeignClient(name = "user-service", url = "${user-service.url:http://localhost:8080}")
public interface UserServiceClient {

  @GetMapping("/api/accounts/{accountId}/balance")
  UserApiResponse<AccountBalanceDto> getAccountBalance(
      @PathVariable("accountId") String accountId
  );

  @PostMapping("/api/accounts/{accountId}/balance/update")
  UserApiResponse<Void> updateAccountBalance(
      @PathVariable("accountId") String accountId,
      @RequestParam("amount") BigDecimal amount,
      @RequestParam("type") String type,
      @RequestParam("description") String description
  );
}