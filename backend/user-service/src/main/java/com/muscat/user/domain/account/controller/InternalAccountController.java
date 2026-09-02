package com.muscat.user.domain.account.controller;

import com.muscat.user.common.util.AuthUtil;
import com.muscat.user.domain.account.service.AccountService;
import com.muscat.user.common.exceptions.AccountException;
import com.muscat.user.common.enums.responses.AccountResponse;
import java.math.BigDecimal;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.security.oauth2.jwt.Jwt;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

/**
 * 내부 서비스 간 통신용 계좌 컨트롤러
 * Trade 서비스에서만 호출되는 API들
 */
@RestController
@RequestMapping("/api/internal/account")
@RequiredArgsConstructor
@Slf4j
public class InternalAccountController {

  private final AccountService accountService;
  private final AuthUtil authUtil;

  // Trade 서비스 전용: USD 잔고 업데이트 (매수/매도)
  @PostMapping("/{accountId}/trade/balance")
  public ResponseEntity<Void> updateTradeBalance(
      @PathVariable Long accountId,
      @RequestParam BigDecimal usdAmount,
      @RequestParam String tradeType,
      @RequestParam String description,
      @AuthenticationPrincipal Jwt jwt) {

    Long userId = authUtil.requireUserId(jwt);

    log.info("거래 USD 잔고 업데이트: userId={}, accountId={}, usdAmount={}, tradeType={}",
        userId, accountId, usdAmount, tradeType);

    try {
      if ("BUY".equals(tradeType)) {
        // 매수: USD 차감
        accountService.updateUsdBalance(accountId, userId, usdAmount.negate(), description);
      } else if ("SELL".equals(tradeType)) {
        // 매도: USD 추가
        accountService.updateUsdBalance(accountId, userId, usdAmount, description);
      } else {
        throw new AccountException(AccountResponse.INVALID_REQUEST);
      }

      log.info("거래 USD 잔고 업데이트 완료: userId={}, accountId={}", userId, accountId);
      return ResponseEntity.ok().build();

    } catch (Exception e) {
      log.error("거래 USD 잔고 업데이트 실패: userId={}, accountId={}, error={}",
          userId, accountId, e.getMessage());
      throw e;
    }
  }
}