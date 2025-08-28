package com.muscat.user.domain.account.service;

import com.muscat.user.domain.account.dto.request.CreateAccountRequestDto;
import com.muscat.user.domain.account.dto.response.BalanceResponseDto;
import com.muscat.user.domain.account.entity.Account;
import java.math.BigDecimal;
import java.util.List;

public interface AccountService {

  // 계좌 생성
  Account createAccount(Long userId, CreateAccountRequestDto request);

  // 사용자의 모든 계좌 조회
  List<Account> getUserAccounts(Long userId);

  // 계좌 잔액 조회
  BalanceResponseDto getAccountBalance(Long accountId, Long userId);

  // KRW 입금
  void depositKrw(Long accountId, Long userId, BigDecimal krwAmount);

  // KRW → USD 환전
  void exchangeKrwToUsd(Long accountId, Long userId, BigDecimal krwAmount, BigDecimal exchangeRate);

  // USD → KRW 환전
  void exchangeUsdToKrw(Long accountId, Long userId, BigDecimal usdAmount, BigDecimal exchangeRate);
}