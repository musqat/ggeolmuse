package com.muscat.trade.domain.controller;

import com.muscat.trade.common.responses.ApiResponse;
import com.muscat.trade.common.enums.BaseResponseEnum;
import com.muscat.trade.infra.client.UserServiceClient;
import com.muscat.trade.infra.client.dto.AccountBalanceDto;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.Authentication;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api/trade/test")
@RequiredArgsConstructor
@Slf4j
public class TestController {

  private final UserServiceClient userServiceClient;

  @GetMapping("/auth")
  public ResponseEntity<ApiResponse<Object>> testAuth(Authentication authentication) {
    log.info("인증 테스트 - 사용자: {}", authentication.getName());
    
    return ResponseEntity.ok(
        ApiResponse.success(BaseResponseEnum.TRADE_VALIDATION_SUCCESS, 
            "인증 성공: " + authentication.getName())
    );
  }

  @GetMapping("/account/{accountId}")
  public ResponseEntity<ApiResponse<AccountBalanceDto>> testAccountIntegration(@PathVariable String accountId) {
    log.info("계좌 연동 테스트 - 계좌ID: {}", accountId);
    
    try {
      var response = userServiceClient.getAccountBalance(accountId);
      
      if (response.getData() != null) {
        log.info("계좌 정보 조회 성공: {}", response.getData());
        return ResponseEntity.ok(
            ApiResponse.success(BaseResponseEnum.TRADE_VALIDATION_SUCCESS, response.getData())
        );
      } else {
        log.warn("계좌 정보 조회 실패: {}", response.getStatusMsg());
        return ResponseEntity.badRequest().body(
            ApiResponse.error(BaseResponseEnum.ACCOUNT_NOT_FOUND)
        );
      }
      
    } catch (Exception e) {
      log.error("계좌 연동 테스트 실패: accountId={}", accountId, e);
      return ResponseEntity.status(503).body(
          ApiResponse.error(BaseResponseEnum.USER_SERVICE_ERROR)
      );
    }
  }
}