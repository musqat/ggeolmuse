package com.muscat.trade.domain.entity;

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

  @Id
  @GeneratedValue(strategy = GenerationType.IDENTITY)
  private Long id; // PK

  @Column(nullable = false)
  private String userId; // 사용자 ID (Keycloak UUID)

  @Column(nullable = false)
  private Long accountId; // 계좌 ID (수수료 정보 참조)

  @Column(nullable = false, length = 10)
  private String symbol; // 주식 심볼 (AAPL, MSFT)

  @Enumerated(EnumType.STRING)
  @Column(nullable = false)
  private TradeType tradeType; // BUY, SELL

  @Column(nullable = false, precision = 15, scale = 6)
  private BigDecimal quantity; // 거래 수량

  @Column(nullable = false, precision = 15, scale = 2)
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
}
