package com.muscat.user.domain.account.entity;

import com.muscat.user.common.enums.type.TransactionType;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.FetchType;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.JoinColumn;
import jakarta.persistence.ManyToOne;
import jakarta.persistence.Table;
import java.math.BigDecimal;
import java.time.LocalDateTime;
import lombok.AccessLevel;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Getter;
import lombok.NoArgsConstructor;
import org.hibernate.annotations.CreationTimestamp;

@Entity
@Table(name = "account_history")
@Getter
@AllArgsConstructor
@Builder
@NoArgsConstructor(access = AccessLevel.PROTECTED)
public class AccountHistory {

  @Id
  @GeneratedValue(strategy = GenerationType.IDENTITY)
  private Long id;

  // 계좌
  @ManyToOne(fetch = FetchType.LAZY)
  @JoinColumn(name = "account_id", nullable = false)
  private Account account;

  // 거래 유형
  @Enumerated(EnumType.STRING)
  @Column(nullable = false, length = 20)
  private TransactionType transactionType;

  // 거래 금액
  @Column(nullable = false, precision = 15, scale = 2)
  private BigDecimal amount;

  // 통화
  @Column(length = 3)
  private String currency;

  // 거래 후 잔액
  @Column(precision = 15, scale = 2)
  private BigDecimal balanceAfter;

  // 거래 설명
  @Column(length = 500)
  private String description;

  // 참조 ID
  @Column(length = 100)
  private String referenceId;

  // 생성일시
  @CreationTimestamp
  @Column(nullable = false, updatable = false)
  private LocalDateTime createdAt;

  // 환전 원래 통화
  @Column(length = 3)
  private String fromCurrency;

  // 환전 변환 통화
  @Column(length = 3)
  private String toCurrency;

  // 환율
  @Column(precision = 10, scale = 6)
  private BigDecimal exchangeRate;

  // 환전 전 금액
  @Column(precision = 15, scale = 2)
  private BigDecimal originalAmount;
}