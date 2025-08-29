package com.muscat.trade.domain.entity;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.Table;
import jakarta.persistence.UniqueConstraint;
import java.math.BigDecimal;
import java.time.LocalDate;
import java.time.LocalDateTime;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;
import org.hibernate.annotations.CreationTimestamp;
import org.hibernate.annotations.UpdateTimestamp;

@Entity
@Table(name = "holdings",
    uniqueConstraints = @UniqueConstraint(columnNames = {"userId", "accountId", "symbol"}))
@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class Holdings {

  @Id
  @GeneratedValue(strategy = GenerationType.UUID)
  private String holdingId; // PK

  @Column(nullable = false)
  private String userId; // 사용자 ID (Keycloak UUID)

  @Column(nullable = false)
  private String accountId; // 계좌 ID

  @Column(nullable = false, length = 10)
  private String symbol; // 주식 심볼 (AAPL, MSFT)

  @Column(nullable = false, precision = 15, scale = 6)
  @Builder.Default
  private BigDecimal totalQuantity = BigDecimal.ZERO; // 현재 보유량

  @Column(nullable = false, precision = 15, scale = 2)
  @Builder.Default
  private BigDecimal avgPurchasePrice = BigDecimal.ZERO; // 평균 매수가 (USD)

  @Column(nullable = false, precision = 15, scale = 2)
  @Builder.Default
  private BigDecimal totalInvestedAmount = BigDecimal.ZERO; // 총 투자금액

  @Column(nullable = false, precision = 15, scale = 2)
  @Builder.Default
  private BigDecimal totalDividends = BigDecimal.ZERO; // 누적 배당금

  @Column(nullable = true)
  private LocalDate lastDividendCalculated; // 마지막 배당 계산일

  @CreationTimestamp
  @Column(nullable = false, updatable = false)
  private LocalDateTime createdAt; // 생성일시

  @UpdateTimestamp
  @Column(nullable = false)
  private LocalDateTime lastUpdatedAt; // 최종 수정일시
}