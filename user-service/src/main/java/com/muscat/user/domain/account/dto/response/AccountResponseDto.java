package com.muscat.user.domain.account.dto.response;

import lombok.Builder;
import lombok.Getter;

import java.math.BigDecimal;
import java.time.LocalDateTime;

@Getter
@Builder
public class AccountResponseDto {

  // 기본 정보
  private Long id;
  private String accountName;
  private String accountNumber;
  private LocalDateTime createdAt;
  
  // 잔액 정보
  private BigDecimal balanceKrw;           // KRW 잔액
  private BigDecimal balanceUsd;           // USD 잔액
  
  // 환율 정보
  private BigDecimal avgExchangeRate;      // 평균 매입 환율
  private BigDecimal totalExchangedKrw;    // 총 환전한 KRW 금액
  
  // 설정 정보
  private BigDecimal commissionRate;       // 수수료율
  private BigDecimal slippageRate;         // 슬리피지율
}