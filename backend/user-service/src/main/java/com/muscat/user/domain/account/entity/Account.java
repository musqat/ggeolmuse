package com.muscat.user.domain.account.entity;

import com.muscat.commonlib.util.MoneyUtils;
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
import jakarta.persistence.Version;
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

  public static final BigDecimal DEFAULT_SLIPPAGE_RATE = new BigDecimal("0.001");

  // 두 환전 누계의 비율과 평균 환율이 어긋났다고 보는 차이(1%)
  private static final BigDecimal TOTALS_TOLERANCE = new BigDecimal("0.01");

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

  // 총 환전으로 받은 USD 금액 (평균 환율 계산용)
  @Column(name = "total_exchanged_usd", precision = 15, scale = 2, nullable = false)
  @Builder.Default
  private BigDecimal totalExchangedUsd = BigDecimal.ZERO;

  // 내 평균 환율 (KRW → USD 환전 시 가중평균)
  @Column(name = "avg_exchange_rate", precision = 10, scale = 6, nullable = false)
  @Builder.Default
  private BigDecimal avgExchangeRate = BigDecimal.ZERO;

  // 수수료율 (0.01 = 1%)
  @Column(name = "commission_rate", precision = 6, scale = 5, nullable = false)
  @Builder.Default
  private BigDecimal commissionRate = BigDecimal.ZERO;

  // 슬리피지율 (0.001 = 0.1%)
  @Column(name = "slippage_rate", precision = 6, scale = 5, nullable = false)
  @Builder.Default
  private BigDecimal slippageRate = DEFAULT_SLIPPAGE_RATE;

  @CreationTimestamp
  private LocalDateTime createdAt;

  @UpdateTimestamp
  private LocalDateTime updatedAt;

  // 낡은 상태로 덮어쓰는 갱신을 막는 버전
  @Version
  private Long version;

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
      throw new com.muscat.user.common.exception.AccountException(
          com.muscat.user.common.enums.response.AccountResponse.INSUFFICIENT_BALANCE
      );
    }
  }

  public void validateSufficientUsdBalance(BigDecimal amount) {
    if (this.balanceUsd.compareTo(amount) < 0) {
      throw new com.muscat.user.common.exception.AccountException(
          com.muscat.user.common.enums.response.AccountResponse.INSUFFICIENT_USD_BALANCE
      );
    }
  }

  // KRW 입금
  public void depositKrw(BigDecimal amount) {
    com.muscat.commonlib.util.MoneyUtils.validatePositiveAmount(amount, "입금 금액");
    this.balanceKrw = this.balanceKrw.add(amount);
  }

  // USD 잔액 조정 (거래 서비스 전용: 양수/음수 모두 가능)
  public void adjustUsdBalance(BigDecimal amount) {
    BigDecimal newBalance = this.balanceUsd.add(amount);

    if (newBalance.compareTo(BigDecimal.ZERO) < 0) {
      throw new com.muscat.user.common.exception.AccountException(
          com.muscat.user.common.enums.response.AccountResponse.INSUFFICIENT_USD_BALANCE
      );
    }

    this.balanceUsd = newBalance;
  }

  // 배당 몫 조정(반환으로 잔액이 마이너스가 되어도 막지 않음)
  public void applyDividendAdjustment(BigDecimal amount) {
    this.balanceUsd = this.balanceUsd.add(amount);
  }

  // KRW -> USD 환전(새 평균이 정상 환율 범위 밖이면 이번 환전 금액으로 두 누계 다시 시작)
  public void executeExchangeKrwToUsd(BigDecimal krwToDeduct, BigDecimal usdToAdd,
                                       BigDecimal newAvgRate, BigDecimal krwAmountForAvg) {
    validateSufficientKrwBalance(krwToDeduct);

    this.balanceKrw = this.balanceKrw.subtract(krwToDeduct);
    this.balanceUsd = this.balanceUsd.add(usdToAdd);
    if (MoneyUtils.isExchangeRateInRange(newAvgRate)) {
      this.totalExchangedKrw = this.totalExchangedKrw.add(krwAmountForAvg);
      this.totalExchangedUsd = this.totalExchangedUsd.add(usdToAdd);
      this.avgExchangeRate = newAvgRate;
    } else {
      this.totalExchangedKrw = krwAmountForAvg;
      this.totalExchangedUsd = usdToAdd;
      this.avgExchangeRate = ratio(krwAmountForAvg, usdToAdd);
    }
  }

  // 평균 환율과 1% 넘게 어긋난 환전 누계 맞춤(평균 환율 0 이면 두 누계 0, 아니면 달러 누계 = 원화 누계 / 평균 환율)
  public void alignExchangeTotals() {
    if (this.avgExchangeRate.signum() == 0) {
      this.totalExchangedKrw = BigDecimal.ZERO;
      this.totalExchangedUsd = BigDecimal.ZERO;
      return;
    }
    if (this.totalExchangedUsd.signum() > 0 && ratio(this.totalExchangedKrw, this.totalExchangedUsd)
        .subtract(this.avgExchangeRate).abs()
        .compareTo(this.avgExchangeRate.multiply(TOTALS_TOLERANCE)) <= 0) {
      return;
    }
    this.totalExchangedUsd = MoneyUtils.roundUsd(
        this.totalExchangedKrw.divide(this.avgExchangeRate, 10, MoneyUtils.ROUND_MODE));
  }

  // USD -> KRW 환전(남은 달러 잔액 비율만큼 두 환전 누계 축소, 달러 잔액이 0 이면 평균 환율도 0)
  public void executeExchangeUsdToKrw(BigDecimal usdToDeduct, BigDecimal krwToAdd) {
    validateSufficientUsdBalance(usdToDeduct);

    BigDecimal usdBefore = this.balanceUsd;
    this.balanceUsd = this.balanceUsd.subtract(usdToDeduct);
    this.balanceKrw = this.balanceKrw.add(krwToAdd);

    BigDecimal remaining = usdBefore.signum() > 0
        ? this.balanceUsd.divide(usdBefore, 10, MoneyUtils.ROUND_MODE)
        : BigDecimal.ZERO;
    this.totalExchangedKrw = MoneyUtils.roundKrw(this.totalExchangedKrw.multiply(remaining));
    this.totalExchangedUsd = MoneyUtils.roundUsd(this.totalExchangedUsd.multiply(remaining));
    if (this.balanceUsd.signum() == 0) {
      this.avgExchangeRate = BigDecimal.ZERO;
    }
  }

  // 원화 / 달러 환율(소수 6자리)
  private static BigDecimal ratio(BigDecimal krw, BigDecimal usd) {
    return MoneyUtils.roundExchangeRate(krw.divide(usd, 10, MoneyUtils.ROUND_MODE));
  }
}
