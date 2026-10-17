package com.muscat.trade.infra.client;

import com.muscat.trade.infra.client.dto.AccountBalanceDto;
import com.muscat.trade.infra.client.dto.DividendSettlementDto;
import java.util.List;
import org.springframework.cloud.openfeign.FeignClient;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;

@FeignClient(name = "user-service", url = "http://user-service:8080")
public interface UserServiceClient {

  @GetMapping("/api/accounts/{accountId}/balance")
  AccountBalanceDto getAccountBalance(
      @PathVariable("accountId") Long accountId
  );

  // user 관리자 경로라 관리자 토큰으로만 됨
  @GetMapping("/api/admin/users/dividend-settlements")
  List<DividendSettlementDto> getDividendSettlements();
}
