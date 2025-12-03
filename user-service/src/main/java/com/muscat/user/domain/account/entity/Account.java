package com.muscat.user.domain.account.entity;

import com.muscat.user.domain.user.entity.User;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.FetchType;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.Index;
import jakarta.persistence.JoinColumn;
import jakarta.persistence.ManyToOne;
import jakarta.persistence.Table;
import java.math.BigDecimal;
import java.time.LocalDateTime;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;
import org.hibernate.annotations.CreationTimestamp;
import org.hibernate.annotations.UpdateTimestamp;

@Entity
@Table(name = "account", indexes = {
    @Index(name = "idx_account_user_id", columnList = "user_id"),
    @Index(name = "idx_account_number", columnList = "account_number", unique = true)
})
@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class Account {

  @Id
  @GeneratedValue(strategy = GenerationType.IDENTITY)
  private Long id;

  @ManyToOne(fetch = FetchType.LAZY)
  @JoinColumn(name = "user_id", nullable = false)
  private User user;

  @Column(name = "account_number", unique = true, nullable = false, length = 20)
  private String accountNumber;

  @Column(name = "account_name", nullable = false, length = 50)
  private String accountName;

  // KRW 잔액
  @Column(name = "balance_krw", precision = 15, scale = 0, nullable = false)
  @Builder.Default
  private BigDecimal balanceKrw = BigDecimal.ZERO;

  // USD 잔액
  @Column(name = "balance_usd", precision = 15, scale = 2, nullable = false)
  @Builder.Default
  private BigDecimal balanceUsd = BigDecimal.ZERO;

  // 총 환전된 KRW 금액 (평균 환율 계산용)
  @Column(name = "total_exchanged_krw", precision = 15, scale = 0, nullable = false)
  @Builder.Default
  private BigDecimal totalExchangedKrw = BigDecimal.ZERO;

  // 내 평균 환율 (KRW → USD 환전 시 가중평균)
  @Column(name = "avg_exchange_rate", precision = 10, scale = 6, nullable = false)
  @Builder.Default
  private BigDecimal avgExchangeRate = BigDecimal.ZERO;

  // 수수료율 (0.01 = 1%)
  @Column(name = "commission_rate", precision = 6, scale = 5, nullable = false)
  @Builder.Default
  private BigDecimal commissionRate = BigDecimal.ZERO;

  // 슬리피지율 (0.01 = 1%)
  @Column(name = "slippage_rate", precision = 6, scale = 5, nullable = false)
  @Builder.Default
  private BigDecimal slippageRate = new BigDecimal("0.01");

  @CreationTimestamp
  private LocalDateTime createdAt;

  @UpdateTimestamp
  private LocalDateTime updatedAt;

  @Override
  public String toString() {
    return String.format("Account[id=%d, number=%s, name=%s, krw=%s, usd=%s]",
        id, accountNumber, accountName, balanceKrw, balanceUsd);
  }

  // ============================================
  // 도메인 메서드 (Domain Methods)
  // ============================================

  // 잔액 검증
  public void validateSufficientKrwBalance(BigDecimal amount) {
    if (this.balanceKrw.compareTo(amount) < 0) {
      throw new com.muscat.user.common.exceptions.AccountException(
          com.muscat.user.common.enums.responses.AccountResponse.INSUFFICIENT_BALANCE
      );
    }
  }

  public void validateSufficientUsdBalance(BigDecimal amount) {
    if (this.balanceUsd.compareTo(amount) < 0) {
      throw new com.muscat.user.common.exceptions.AccountException(
          com.muscat.user.common.enums.responses.AccountResponse.INSUFFICIENT_USD_BALANCE
      );
    }
  }

  // 삭제 가능 여부 검증
  public void validateDeletable() {
    if (this.balanceKrw.compareTo(BigDecimal.ZERO) > 0 ||
        this.balanceUsd.compareTo(BigDecimal.ZERO) > 0) {
      throw new com.muscat.user.common.exceptions.AccountException(
          com.muscat.user.common.enums.responses.AccountResponse.CANNOT_DELETE_ACCOUNT_WITH_BALANCE
      );
    }
  }

  // KRW 입금
  public void depositKrw(BigDecimal amount) {
    com.muscat.commonlib.util.MoneyUtils.validatePositiveAmount(amount, "입금 금액");
    this.balanceKrw = this.balanceKrw.add(amount);
  }

  // USD 입금
  public void depositUsd(BigDecimal amount) {
    com.muscat.commonlib.util.MoneyUtils.validatePositiveAmount(amount, "입금 금액");
    this.balanceUsd = this.balanceUsd.add(amount);
  }

  // KRW 출금
  public void withdrawKrw(BigDecimal amount) {
    validateSufficientKrwBalance(amount);
    this.balanceKrw = this.balanceKrw.subtract(amount);
  }

  // USD 출금
  public void withdrawUsd(BigDecimal amount) {
    validateSufficientUsdBalance(amount);
    this.balanceUsd = this.balanceUsd.subtract(amount);
  }

  // USD 잔액 조정 (거래 서비스 전용: 양수/음수 모두 가능)
  public void adjustUsdBalance(BigDecimal amount) {
    BigDecimal newBalance = this.balanceUsd.add(amount);

    if (newBalance.compareTo(BigDecimal.ZERO) < 0) {
      throw new com.muscat.user.common.exceptions.AccountException(
          com.muscat.user.common.enums.responses.AccountResponse.INSUFFICIENT_USD_BALANCE
      );
    }

    this.balanceUsd = newBalance;
  }

  // KRW -> USD 환전
  public void executeExchangeKrwToUsd(BigDecimal krwToDeduct, BigDecimal usdToAdd,
                                       BigDecimal newAvgRate, BigDecimal krwAmountForAvg) {
    validateSufficientKrwBalance(krwToDeduct);

    this.balanceKrw = this.balanceKrw.subtract(krwToDeduct);
    this.balanceUsd = this.balanceUsd.add(usdToAdd);
    this.totalExchangedKrw = this.totalExchangedKrw.add(krwAmountForAvg);
    this.avgExchangeRate = newAvgRate;
  }

  // USD -> KRW 환전
  public void executeExchangeUsdToKrw(BigDecimal usdToDeduct, BigDecimal krwToAdd) {
    validateSufficientUsdBalance(usdToDeduct);

    this.balanceUsd = this.balanceUsd.subtract(usdToDeduct);
    this.balanceKrw = this.balanceKrw.add(krwToAdd);

    // USD 잔액이 0이면 평균환율 리셋
    if (this.balanceUsd.compareTo(BigDecimal.ZERO) == 0) {
      this.avgExchangeRate = BigDecimal.ZERO;
      this.totalExchangedKrw = BigDecimal.ZERO;
    }
  }
}
