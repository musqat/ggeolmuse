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


  /**
   * 기본 정보 문자열 (디버깅/로깅용)
   */
  @Override
  public String toString() {
    return String.format("Account[id=%d, number=%s, name=%s, krw=%s, usd=%s]",
        id, accountNumber, accountName, balanceKrw, balanceUsd);
  }
}