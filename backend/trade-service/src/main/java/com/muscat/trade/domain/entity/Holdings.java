package com.muscat.trade.domain.entity;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.Index;
import jakarta.persistence.Table;
import jakarta.persistence.UniqueConstraint;
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
@Table(name = "holdings",
    uniqueConstraints = @UniqueConstraint(columnNames = {"userId", "accountId", "symbol"}),
    indexes = {
        @Index(name = "idx_holdings_user_account", columnList = "userId, accountId"),
        @Index(name = "idx_holdings_user_symbol", columnList = "userId, symbol"),
        @Index(name = "idx_holdings_account_symbol", columnList = "accountId, symbol")
    })
@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class Holdings {

  @Id
  @GeneratedValue(strategy = GenerationType.IDENTITY)
  private Long id; // PK

  @Column(nullable = false)
  private String userId; // 사용자 ID (Keycloak UUID)

  @Column(nullable = false)
  private Long accountId; // 계좌 ID

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


  @CreationTimestamp
  @Column(nullable = false, updatable = false)
  private LocalDateTime createdAt; // 생성일시

  @UpdateTimestamp
  @Column(nullable = false)
  private LocalDateTime lastUpdatedAt; // 최종 수정일시

  // ============================================
  // 도메인 메서드 (Domain Methods)
  // ============================================

  // 매수: 평균 매수가 재계산 (가중 평균)
  public void addPurchase(BigDecimal quantity, BigDecimal price, int pricePrecision) {
    BigDecimal currentTotalValue = this.totalQuantity.multiply(this.avgPurchasePrice);
    BigDecimal newTotalValue = currentTotalValue.add(quantity.multiply(price));
    BigDecimal newTotalQuantity = this.totalQuantity.add(quantity);
    BigDecimal newAvgPrice = newTotalValue.divide(newTotalQuantity, pricePrecision,
        java.math.RoundingMode.HALF_UP);

    this.totalQuantity = newTotalQuantity;
    this.avgPurchasePrice = newAvgPrice;
    this.totalInvestedAmount = this.totalInvestedAmount.add(quantity.multiply(price));
  }

  // 매도: 수량 감소 (평균 매수가는 유지)
  public void sellShares(BigDecimal quantity, int sellRatioPrecision) {
    BigDecimal sellRatio = quantity.divide(this.totalQuantity, sellRatioPrecision,
        java.math.RoundingMode.HALF_UP);
    BigDecimal soldAmount = this.totalInvestedAmount.multiply(sellRatio);

    this.totalQuantity = this.totalQuantity.subtract(quantity);
    this.totalInvestedAmount = this.totalInvestedAmount.subtract(soldAmount);
  }

  // 보유 여부 확인
  public boolean hasShares() {
    return this.totalQuantity.compareTo(BigDecimal.ZERO) > 0;
  }
}
