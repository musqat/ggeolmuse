package com.muscat.marketdata.domain.entity;

import jakarta.persistence.*;
import lombok.*;
import java.math.BigDecimal;
import java.time.LocalDate;

/**
 * 일봉 데이터 (OHLCV + 백테스트용 보정값)
 */
@Getter @Setter
@NoArgsConstructor
@AllArgsConstructor
@Builder
@Entity
@Table(name = "candle", 
    uniqueConstraints = @UniqueConstraint(columnNames = {"symbol", "date", "currency"}),
    indexes = {
        @Index(name = "idx_candle_symbol_date", columnList = "symbol,date")
    })
public class Candle {

  @Id
  @GeneratedValue(strategy = GenerationType.IDENTITY)
  private Long id;

  @Column(name = "symbol", nullable = false, length = 16)
  private String symbol;

  @Column(name = "date", nullable = false)
  private LocalDate date;

  @Column(name = "currency", nullable = false, length = 3)
  private String currency;

  // ===== 원시 OHLCV 데이터 =====

  @Column(name = "open", precision = 19, scale = 8)
  private BigDecimal open;

  @Column(name = "high", precision = 19, scale = 8)
  private BigDecimal high;

  @Column(name = "low", precision = 19, scale = 8)
  private BigDecimal low;

  @Column(name = "close", precision = 19, scale = 8)
  private BigDecimal close;

  @Column(name = "volume")
  private Long volume;

  // ===== 백테스트용 보정 필드들 =====

  /**
   * 배당/분할 반영 보정 종가
   * - 백테스트 수익률 계산의 기본값
   * - 과거 데이터를 현재 주식 구조 기준으로 역산 조정
   * - 예: 4:1 분할 시 과거 $400 → $100으로 조정
   */
  @Column(name = "adjusted_close", precision = 19, scale = 8, nullable = false)
  private BigDecimal adjustedClose;

  /**
   * 당일 지급된 배당금액 (주당 기준)
   * - 배당락일에만 값이 있고, 평상시에는 0
   * - 백테스트 시 현금흐름 계산에 사용
   * - 예: 배당락일에 주당 $1.50 지급 → 1.50 저장
   */
  @Column(name = "dividend_amount", precision = 19, scale = 8, nullable = false)
  @Builder.Default
  private BigDecimal dividendAmount = BigDecimal.ZERO;

  /**
   * 당일 주식분할 계수
   * - 주식분할/합병이 없는 평상시에는 1.0
   * - 분할 시: 분할 비율 (예: 2:1 분할 → 2.0)
   * - 합병 시: 합병 비율 (예: 1:2 합병 → 0.5)
   * - adjustedClose 계산에 사용되는 원천 데이터
   */
  @Column(name = "split_coefficient", precision = 19, scale = 8, nullable = false)
  @Builder.Default
  private BigDecimal splitCoefficient = BigDecimal.ONE;


  // ===== 비즈니스 로직 메서드들 =====

  /**
   * 백테스트용 실효가격 (adjustedClose 우선)
   */
  public BigDecimal getEffectivePrice() {
    return adjustedClose != null ? adjustedClose : close;
  }

  /**
   * 당일 기업액션 발생 여부
   */
  public boolean hasCorporateAction() {
    return dividendAmount.compareTo(BigDecimal.ZERO) > 0
        || splitCoefficient.compareTo(BigDecimal.ONE) != 0;
  }

  /**
   * OHLC 관계 유효성 검증
   */
  public boolean isValidOhlc() {
    if (open == null || high == null || low == null || close == null) {
      return false;
    }
    return low.compareTo(open) <= 0
        && low.compareTo(close) <= 0
        && high.compareTo(open) >= 0
        && high.compareTo(close) >= 0;
  }
}