package com.muscat.marketdata.domain.entity;

import jakarta.persistence.*;
import lombok.*;
import java.math.BigDecimal;

/**
 * 배당 이벤트 (백테스트 상세 분석용)
 * Candle.dividendAmount로도 충분하지만, 세밀한 배당 전략에는 이 테이블 활용
 */
@Getter @Setter
@NoArgsConstructor
@AllArgsConstructor
@Builder
@Entity
@Table(name = "dividend", indexes = {
    @Index(name = "idx_dividend_symbol_ex_date", columnList = "symbol,ex_date", unique = true),
    @Index(name = "idx_dividend_ex_date", columnList = "ex_date")
})
public class Dividend {

  @EmbeddedId
  private DividendId id; // (symbol, exDate)

  /**
   * 주당 배당금액
   */
  @Column(name = "amount", precision = 19, scale = 8, nullable = false)
  private BigDecimal amount;

  /**
   * 배당 통화 (NASDAQ 100은 대부분 USD)
   */
  @Column(name = "currency", length = 3, nullable = false)
  @Builder.Default
  private String currency = "USD";

  // ===== 비즈니스 로직 메서드들 =====

  /**
   * 배당락일이 지났는지 확인
   */
  public boolean isExDatePassed() {
    return id != null && id.getExDate() != null &&
        id.getExDate().isBefore(java.time.LocalDate.now());
  }

  /**
   * 백테스트용 현금흐름 금액 반환
   */
  public BigDecimal getCashFlowAmount() {
    return amount;
  }
}