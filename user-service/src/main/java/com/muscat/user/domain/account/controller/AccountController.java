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
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.Parameter;
import io.swagger.v3.oas.annotations.tags.Tag;
import io.swagger.v3.oas.annotations.responses.ApiResponse;
import io.swagger.v3.oas.annotations.responses.ApiResponses;
import io.swagger.v3.oas.annotations.security.SecurityRequirement;
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

@Tag(name = "Account Management", description = "계좌 관리 API")
@RestController
@RequestMapping("/api/accounts")
@RequiredArgsConstructor
@Slf4j
@SecurityRequirement(name = "bearerAuth")
public class AccountController {

  private final AccountService accountService;
  private final AuthUtil authUtil;
  private final UserMapper userMapper;

  @Operation(summary = "계좌 생성", description = "새로운 계좌를 생성합니다")
  @ApiResponses(value = {
      @ApiResponse(responseCode = "201", description = "계좌 생성 성공"),
      @ApiResponse(responseCode = "400", description = "잘못된 요청"),
      @ApiResponse(responseCode = "401", description = "인증 실패")
  })
  @PostMapping
  public ResponseEntity<AccountResponseDto> createAccount(
      @Valid @RequestBody CreateAccountRequestDto request,
      @Parameter(hidden = true) @AuthenticationPrincipal Jwt jwt) {

    Long userId = authUtil.requireUserId(jwt);
    Account account = accountService.createAccount(userId, request);

    log.info("계좌 생성 요청: 사용자={}, 계좌명={}", userId, request.getAccountName());

    AccountResponseDto accountDto = userMapper.toAccountResponseDto(account);
    return ResponseEntity.status(201).body(accountDto);
  }

  @Operation(summary = "계좌 목록 조회", description = "사용자의 모든 계좌 목록을 조회합니다")
  @ApiResponses(value = {
      @ApiResponse(responseCode = "200", description = "조회 성공"),
      @ApiResponse(responseCode = "401", description = "인증 실패")
  })
  @GetMapping
  public ResponseEntity<List<AccountSummaryDto>> getUserAccounts(
      @Parameter(hidden = true) @AuthenticationPrincipal Jwt jwt) {

    Long userId = authUtil.requireUserId(jwt);
    List<Account> accounts = accountService.getUserAccounts(userId);
    List<AccountSummaryDto> accountDtos = accounts.stream()
        .map(userMapper::toAccountSummaryDto)
        .toList();

    return ResponseEntity.ok(accountDtos);
  }

  @Operation(summary = "계좌 잔액 조회", description = "특정 계좌의 잔액을 조회합니다")
  @ApiResponses(value = {
      @ApiResponse(responseCode = "200", description = "조회 성공"),
      @ApiResponse(responseCode = "404", description = "계좌를 찾을 수 없음"),
      @ApiResponse(responseCode = "401", description = "인증 실패")
  })
  @GetMapping("/{accountId}/balance")
  public ResponseEntity<BalanceResponseDto> getAccountBalance(
      @Parameter(description = "계좌 ID") @PathVariable Long accountId,
      @Parameter(hidden = true) @AuthenticationPrincipal Jwt jwt) {

    Long userId = authUtil.requireUserId(jwt);
    BalanceResponseDto balance = accountService.getAccountBalance(accountId, userId);

    return ResponseEntity.ok(balance);
  }

  @Operation(summary = "KRW 입금", description = "계좌에 한국원을 입금합니다")
  @ApiResponses(value = {
      @ApiResponse(responseCode = "200", description = "입금 성공"),
      @ApiResponse(responseCode = "400", description = "잘못된 요청"),
      @ApiResponse(responseCode = "404", description = "계좌를 찾을 수 없음"),
      @ApiResponse(responseCode = "401", description = "인증 실패")
  })
  @PostMapping("/{accountId}/deposit")
  public ResponseEntity<Void> depositKrw(
      @Parameter(description = "계좌 ID") @PathVariable Long accountId,
      @Valid @RequestBody KrwDepositRequestDto request,
      @Parameter(hidden = true) @AuthenticationPrincipal Jwt jwt) {

    Long userId = authUtil.requireUserId(jwt);
    accountService.depositKrw(accountId, userId, request.getKrwAmount());

    log.info("KRW 입금 요청: 사용자={}, 계좌={}, 금액={}원",
        userId, accountId, request.getKrwAmount());

    return ResponseEntity.ok().build();
  }

  @Operation(summary = "환전", description = "KRW와 USD 간 환전을 수행합니다")
  @ApiResponses(value = {
      @ApiResponse(responseCode = "200", description = "환전 성공"),
      @ApiResponse(responseCode = "400", description = "잘못된 요청 또는 지원하지 않는 통화"),
      @ApiResponse(responseCode = "404", description = "계좌를 찾을 수 없음"),
      @ApiResponse(responseCode = "401", description = "인증 실패")
  })
  @PostMapping("/{accountId}/exchange")
  public ResponseEntity<Void> exchangeCurrency(
      @Parameter(description = "계좌 ID") @PathVariable Long accountId,
      @Valid @RequestBody ExchangeRequestDto request,
      @Parameter(hidden = true) @AuthenticationPrincipal Jwt jwt) {

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

  @Operation(summary = "날짜 기반 환전", description = "특정 날짜의 환율을 자동으로 조회하여 환전을 수행합니다")
  @ApiResponses(value = {
      @ApiResponse(responseCode = "200", description = "환전 성공"),
      @ApiResponse(responseCode = "400", description = "잘못된 요청 또는 지원하지 않는 통화"),
      @ApiResponse(responseCode = "404", description = "계좌를 찾을 수 없음 또는 해당 날짜 환율 없음"),
      @ApiResponse(responseCode = "401", description = "인증 실패")
  })
  @PostMapping("/{accountId}/exchange/by-date")
  public ResponseEntity<Void> exchangeCurrencyByDate(
      @Parameter(description = "계좌 ID") @PathVariable Long accountId,
      @Valid @RequestBody ExchangeByDateRequestDto request,
      @Parameter(hidden = true) @AuthenticationPrincipal Jwt jwt) {

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

  @Operation(summary = "현재 환율 조회", description = "실시간 USD/KRW 환율을 조회합니다")
  @ApiResponses(value = {
      @ApiResponse(responseCode = "200", description = "환율 조회 성공"),
      @ApiResponse(responseCode = "503", description = "외부 서비스 연결 실패")
  })
  @GetMapping("/exchange-rates/current")
  public ResponseEntity<BigDecimal> getCurrentExchangeRate() {
    log.debug("현재 환율 조회 요청");
    
    BigDecimal currentRate = accountService.getCurrentExchangeRate();
    
    return ResponseEntity.ok(currentRate);
  }

  @Operation(summary = "날짜별 환율 조회", description = "특정 날짜의 USD/KRW 환율을 조회합니다")
  @ApiResponses(value = {
      @ApiResponse(responseCode = "200", description = "환율 조회 성공"),
      @ApiResponse(responseCode = "404", description = "해당 날짜 환율 데이터 없음"),
      @ApiResponse(responseCode = "503", description = "외부 서비스 연결 실패")
  })
  @GetMapping("/exchange-rates/{date}")
  public ResponseEntity<BigDecimal> getExchangeRateByDate(
      @Parameter(description = "조회할 날짜 (YYYY-MM-DD)", example = "2024-01-15")
      @PathVariable @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate date) {
    
    log.debug("날짜별 환율 조회 요청: {}", date);
    
    BigDecimal rate = accountService.getExchangeRateByDate(date);
    
    return ResponseEntity.ok(rate);
  }

  @Operation(summary = "수동 환율 검증", description = "사용자가 입력한 환율을 검증합니다")
  @ApiResponses(value = {
      @ApiResponse(responseCode = "200", description = "환율 검증 성공"),
      @ApiResponse(responseCode = "400", description = "잘못된 환율 값")
  })
  @PostMapping("/exchange-rates/validate")
  public ResponseEntity<BigDecimal> validateManualExchangeRate(
      @Parameter(description = "검증할 환율 값", example = "1300.50")
      @RequestParam BigDecimal rate) {
    
    log.debug("수동 환율 검증 요청: {}", rate);
    
    BigDecimal validatedRate = accountService.createManualExchangeRate(rate);
    
    return ResponseEntity.ok(validatedRate);
  }
}