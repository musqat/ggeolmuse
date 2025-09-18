package com.muscat.user.domain.account.controller;

import com.muscat.user.domain.account.dto.request.CreateAccountRequestDto;
import com.muscat.user.domain.account.dto.response.AccountResponseDto;
import com.muscat.user.domain.account.dto.response.AccountSummaryDto;
import com.muscat.user.domain.account.dto.response.BalanceResponseDto;
import com.muscat.user.domain.account.dto.request.KrwDepositRequestDto;
import com.muscat.user.domain.account.dto.request.ExchangeRequestDto;
import com.muscat.user.domain.account.dto.request.ExchangeByDateRequestDto;
import com.muscat.user.domain.account.entity.Account;
import com.muscat.user.domain.account.service.AccountService;
import com.muscat.user.domain.user.mapper.UserMapper;
import com.muscat.user.common.enums.responses.AccountResponse;
import com.muscat.user.common.util.AuthUtil;
import com.muscat.user.common.exceptions.AccountException;
import jakarta.validation.Valid;
import java.math.BigDecimal;
import java.time.LocalDate;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.format.annotation.DateTimeFormat;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.security.oauth2.jwt.Jwt;
import org.springframework.web.bind.annotation.*;

import java.util.List;

@RestController
@RequestMapping("/api/accounts")
@RequiredArgsConstructor
@Slf4j
public class AccountController {

  private final AccountService accountService;
  private final AuthUtil authUtil;
  private final UserMapper userMapper;

  // 계좌 생성
  @PostMapping
  public ResponseEntity<AccountResponseDto> createAccount(
      @Valid @RequestBody CreateAccountRequestDto request,
      @AuthenticationPrincipal Jwt jwt) {

    Long userId = authUtil.requireUserId(jwt);
    Account account = accountService.createAccount(userId, request);

    log.info("계좌 생성 요청: 사용자={}, 계좌명={}", userId, request.getAccountName());

    AccountResponseDto accountDto = userMapper.toAccountResponseDto(account);
    return ResponseEntity.status(201).body(accountDto);
  }

  // 내 계좌 목록 조회
  @GetMapping
  public ResponseEntity<List<AccountSummaryDto>> getUserAccounts(@AuthenticationPrincipal Jwt jwt) {

    Long userId = authUtil.requireUserId(jwt);
    List<Account> accounts = accountService.getUserAccounts(userId);
    List<AccountSummaryDto> accountDtos = accounts.stream()
        .map(userMapper::toAccountSummaryDto)
        .toList();

    return ResponseEntity.ok(accountDtos);
  }

  // 계좌 잔액 조회
  @GetMapping("/{accountId}/balance")
  public ResponseEntity<BalanceResponseDto> getAccountBalance(
      @PathVariable Long accountId,
      @AuthenticationPrincipal Jwt jwt) {

    Long userId = authUtil.requireUserId(jwt);
    BalanceResponseDto balance = accountService.getAccountBalance(accountId, userId);

    return ResponseEntity.ok(balance);
  }

  // KRW 입금
  @PostMapping("/{accountId}/deposit")
  public ResponseEntity<Void> depositKrw(
      @PathVariable Long accountId,
      @Valid @RequestBody KrwDepositRequestDto request,
      @AuthenticationPrincipal Jwt jwt) {

    Long userId = authUtil.requireUserId(jwt);
    accountService.depositKrw(accountId, userId, request.getKrwAmount());

    log.info("KRW 입금 요청: 사용자={}, 계좌={}, 금액={}원",
        userId, accountId, request.getKrwAmount());

    return ResponseEntity.ok().build();
  }

  // 환전 (양방향 지원)
  @PostMapping("/{accountId}/exchange")
  public ResponseEntity<Void> exchangeCurrency(
      @PathVariable Long accountId,
      @Valid @RequestBody ExchangeRequestDto request,
      @AuthenticationPrincipal Jwt jwt) {

    Long userId = authUtil.requireUserId(jwt);

    if ("KRW".equals(request.getFromCurrency()) && "USD".equals(request.getToCurrency())) {
      accountService.exchangeKrwToUsd(accountId, userId, request.getOriginalAmount(), request.getExchangeRate());
    } else if ("USD".equals(request.getFromCurrency()) && "KRW".equals(request.getToCurrency())) {
      accountService.exchangeUsdToKrw(accountId, userId, request.getOriginalAmount(), request.getExchangeRate());
    } else {
      throw new AccountException(AccountResponse.INVALID_CURRENCY);
    }

    log.info("환전 요청: 사용자={}, 계좌={}, {} {} → {} (환율: {})",
        userId, accountId, request.getOriginalAmount(), request.getFromCurrency(),
        request.getToCurrency(), request.getExchangeRate());

    return ResponseEntity.ok().build();
  }

  // 날짜 기반 자동 환율 환전
  @PostMapping("/{accountId}/exchange/by-date")
  public ResponseEntity<Void> exchangeCurrencyByDate(
      @PathVariable Long accountId,
      @Valid @RequestBody ExchangeByDateRequestDto request,
      @AuthenticationPrincipal Jwt jwt) {

    Long userId = authUtil.requireUserId(jwt);

    // 해당 날짜의 환율 자동 조회
    BigDecimal exchangeRate = accountService.getExchangeRateByDate(request.getExchangeDate());

    if ("KRW".equals(request.getFromCurrency()) && "USD".equals(request.getToCurrency())) {
      accountService.exchangeKrwToUsd(accountId, userId, request.getOriginalAmount(), exchangeRate);
    } else if ("USD".equals(request.getFromCurrency()) && "KRW".equals(request.getToCurrency())) {
      accountService.exchangeUsdToKrw(accountId, userId, request.getOriginalAmount(), exchangeRate);
    } else {
      throw new AccountException(AccountResponse.INVALID_CURRENCY);
    }

    log.info("날짜 기반 환전 완료: 사용자={}, 계좌={}, {} {} → {} (날짜: {}, 환율: {})",
        userId, accountId, request.getOriginalAmount(), request.getFromCurrency(),
        request.getToCurrency(), request.getExchangeDate(), exchangeRate);

    return ResponseEntity.ok().build();
  }

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

      log.info("거래 USD 잔고 업데이트 완료: accountId={}, usdAmount={}, type={}", 
          accountId, usdAmount, tradeType);

      return ResponseEntity.ok().build();
          
    } catch (Exception e) {
      log.error("거래 USD 잔고 업데이트 실패: accountId={}, usdAmount={}, type={}", 
          accountId, usdAmount, tradeType, e);
      throw e;
    }
  }

  // 현재 환율 조회 (Feign으로 market-data에서 가져오기)
  @GetMapping("/exchange-rates/current")
  public ResponseEntity<BigDecimal> getCurrentExchangeRate() {
    log.debug("현재 환율 조회 요청");
    
    BigDecimal currentRate = accountService.getCurrentExchangeRate();
    
    return ResponseEntity.ok(currentRate);
  }

  // 특정 날짜 환율 조회
  @GetMapping("/exchange-rates/{date}")
  public ResponseEntity<BigDecimal> getExchangeRateByDate(
      @PathVariable @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate date) {
    
    log.debug("날짜별 환율 조회 요청: {}", date);
    
    BigDecimal rate = accountService.getExchangeRateByDate(date);
    
    return ResponseEntity.ok(rate);
  }

  // 수동 환율 입력/검증
  @PostMapping("/exchange-rates/validate")
  public ResponseEntity<BigDecimal> validateManualExchangeRate(
      @RequestParam BigDecimal rate) {
    
    log.debug("수동 환율 검증 요청: {}", rate);
    
    BigDecimal validatedRate = accountService.createManualExchangeRate(rate);
    
    return ResponseEntity.ok(validatedRate);
  }
}