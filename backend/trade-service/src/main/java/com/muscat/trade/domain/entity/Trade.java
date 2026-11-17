package com.muscat.trade.domain.entity;

import com.muscat.trade.common.enums.type.TradeStatus;
import com.muscat.trade.common.enums.type.TradeType;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.Index;
import jakarta.persistence.Table;
import java.math.BigDecimal;
import java.math.RoundingMode;
import java.time.LocalDate;
import java.time.LocalDateTime;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;
import org.hibernate.annotations.CreationTimestamp;

@Entity
@Table(name = "trades", indexes = {
  @Index(name = "idx_trades_user_symbol_date", columnList = "userId, symbol, tradeDate"),
  @Index(name = "idx_trades_user_executed", columnList = "userId, executedAt"),
  @Index(name = "idx_trades_user_account", columnList = "userId, accountId"),
  @Index(name = "idx_trades_symbol_date", columnList = "symbol, tradeDate")
})
@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class Trade {

  // price · split_ratio 컬럼의 소수 자리
  private static final int PRICE_SCALE = 6;
  private static final int RATIO_SCALE = 8;

  @Id
  @GeneratedValue(strategy = GenerationType.IDENTITY)
  private Long id; // PK

  @Column(nullable = false)
  private String userId; // 사용자 ID (Keycloak UUID)

  @Column(nullable = false)
  private Long accountId; // 계좌 ID (수수료 정보 참조)

  @Column(nullable = false, length = 16)
  private String symbol; // 주식 심볼 (AAPL, MSFT)

  @Enumerated(EnumType.STRING)
  @Column(nullable = false)
  private TradeType tradeType; // BUY, SELL

  @Column(nullable = false, precision = 15, scale = 6)
  private BigDecimal quantity; // 거래 수량

  @Column(nullable = false, precision = 19, scale = 6)
  private BigDecimal price; // 체결가 (USD)

  @Column(nullable = false, precision = 15, scale = 2)
  private BigDecimal totalAmount; // 총 거래금액 (수수료 포함)

  @Column(nullable = false, precision = 10, scale = 2)
  @Builder.Default
  private BigDecimal fee = BigDecimal.ZERO; // 수수료

  @Column(nullable = false)
  private LocalDate tradeDate; // 거래일 (과거 거래 지원)

  @Column(nullable = false)
  private LocalDateTime executedAt; // 거래 실행 시각

  @CreationTimestamp
  @Column(nullable = false, updatable = false)
  private LocalDateTime createdAt; // 레코드 생성일시

  private LocalDateTime eventPublishedAt; // 체결 이벤트 발행 시각(비어 있으면 다시 발행 대상)

  @Enumerated(EnumType.STRING)
  @Column(nullable = false, length = 20)
  @Builder.Default
  private TradeStatus status = TradeStatus.COMPLETED; // 체결 상태

  @Column(length = 50)
  private String cancelReason; // 취소 사유 (AccountResponse 이름)

  private LocalDateTime cancelledAt; // 취소 시각

  @Column(precision = 19, scale = 8)
  private BigDecimal splitRatio; // 분할로 맞춘 비율 누적(분할 뒤 주식 수 ÷ 처음 주식 수)

  @Column(precision = 15, scale = 6)
  private BigDecimal preSplitQuantity; // 분할로 맞추기 전 처음 수량

  @Column(precision = 19, scale = 6)
  private BigDecimal preSplitPrice; // 분할로 맞추기 전 처음 체결가

  private LocalDateTime splitAppliedAt; // 마지막으로 분할을 반영한 시각

  // 잔액 정산이 끝난 대기 체결만 완료
  public void complete() {
    if (this.status == TradeStatus.PENDING) {
      this.status = TradeStatus.COMPLETED;
    }
  }

  // 잔액 반영 실패로 취소
  public void cancel(String reason, LocalDateTime at) {
    this.status = TradeStatus.CANCELLED;
    this.cancelReason = reason;
    this.cancelledAt = at;
  }

  // 수량을 분할 뒤 수량으로, 체결가를 분할 비율로 나눈 값으로 바꿈(처음 수량 · 체결가는 첫 분할 때 한 번, 총액 · 수수료는 그대로)
  public void applySplit(BigDecimal ratio, BigDecimal splitQuantity, LocalDateTime at) {
    if (this.preSplitQuantity == null) {
      this.preSplitQuantity = this.quantity;
      this.preSplitPrice = this.price;
    }
    this.quantity = splitQuantity;
    this.splitAppliedAt = at;
    this.price = this.price.divide(ratio, PRICE_SCALE, RoundingMode.HALF_UP);
    this.splitRatio = (this.splitRatio == null ? BigDecimal.ONE : this.splitRatio).multiply(ratio)
      .setScale(RATIO_SCALE, RoundingMode.HALF_UP);
  }
}
