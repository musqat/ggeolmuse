package com.muscat.user.domain.account.controller;

import com.muscat.user.domain.account.dto.request.CreateAccountRequestDto;
import com.muscat.user.domain.account.dto.response.AccountResponseDto;
import com.muscat.user.domain.account.dto.response.AccountSummaryDto;
import com.muscat.user.domain.account.dto.response.BalanceResponseDto;
import com.muscat.user.domain.account.dto.request.KrwDepositRequestDto;
import com.muscat.user.domain.account.dto.request.ExchangeRequestDto;
import com.muscat.user.domain.account.entity.Account;
import com.muscat.user.domain.account.service.AccountService;
import com.muscat.user.domain.user.mapper.UserMapper;
import com.muscat.user.common.responses.ApiResponse;
import com.muscat.user.common.responses.AccountResponse;
import com.muscat.user.common.util.AuthUtil;
import com.muscat.user.common.exceptions.AccountException;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.Authentication;
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
  public ResponseEntity<ApiResponse<AccountResponseDto>> createAccount(
      @Valid @RequestBody CreateAccountRequestDto request,
      Authentication authentication) {

    Long userId = authUtil.requireUserId(authentication);
    Account account = accountService.createAccount(userId, request);

    log.info("계좌 생성 요청: 사용자={}, 계좌명={}", userId, request.getAccountName());

    AccountResponseDto accountDto = userMapper.toAccountResponseDto(account);
    return ResponseEntity.status(201)
        .body(ApiResponse.success(AccountResponse.ACCOUNT_CREATED, accountDto));
  }

  // 내 계좌 목록 조회
  @GetMapping
  public ResponseEntity<ApiResponse<List<AccountSummaryDto>>> getUserAccounts(Authentication authentication) {

    Long userId = authUtil.requireUserId(authentication);
    List<Account> accounts = accountService.getUserAccounts(userId);
    List<AccountSummaryDto> accountDtos = accounts.stream()
        .map(userMapper::toAccountSummaryDto)
        .toList();

    return ResponseEntity.ok(
        ApiResponse.success(AccountResponse.ACCOUNT_FOUND, accountDtos));
  }

  // 계좌 잔액 조회
  @GetMapping("/{accountId}/balance")
  public ResponseEntity<ApiResponse<BalanceResponseDto>> getAccountBalance(
      @PathVariable Long accountId,
      Authentication authentication) {

    Long userId = authUtil.requireUserId(authentication);
    BalanceResponseDto balance = accountService.getAccountBalance(accountId, userId);

    return ResponseEntity.ok(
        ApiResponse.success(AccountResponse.ACCOUNT_FOUND, balance));
  }

  // KRW 입금
  @PostMapping("/{accountId}/deposit")
  public ResponseEntity<ApiResponse<Void>> depositKrw(
      @PathVariable Long accountId,
      @Valid @RequestBody KrwDepositRequestDto request,
      Authentication authentication) {

    Long userId = authUtil.requireUserId(authentication);
    accountService.depositKrw(accountId, userId, request.getKrwAmount());

    log.info("KRW 입금 요청: 사용자={}, 계좌={}, 금액={}원",
        userId, accountId, request.getKrwAmount());

    return ResponseEntity.ok(
        ApiResponse.success(AccountResponse.DEPOSIT_SUCCESS));
  }

  // 환전 (양방향 지원)
  @PostMapping("/{accountId}/exchange")
  public ResponseEntity<ApiResponse<Void>> exchangeCurrency(
      @PathVariable Long accountId,
      @Valid @RequestBody ExchangeRequestDto request,
      Authentication authentication) {

    Long userId = authUtil.requireUserId(authentication);

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

    return ResponseEntity.ok(
        ApiResponse.success(AccountResponse.EXCHANGE_SUCCESS));
  }
}