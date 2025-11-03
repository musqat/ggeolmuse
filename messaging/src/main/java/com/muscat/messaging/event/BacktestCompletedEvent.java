package com.muscat.messaging.event;

import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.EqualsAndHashCode;
import lombok.NoArgsConstructor;
import lombok.experimental.SuperBuilder;

import java.math.BigDecimal;
import java.time.LocalDate;

/**
 * 백테스트 완료 이벤트
 *
 * backtest-service에서 발행되며 다음 서비스에서 소비됩니다:
 * - notification-service: 사용자에게 완료 알림
 * - analytics-service: 백테스트 성능 추적
 */
@Data
@SuperBuilder
@NoArgsConstructor
@AllArgsConstructor
@EqualsAndHashCode(callSuper = true)
public class BacktestCompletedEvent extends BaseEvent {

    /**
     * 백테스트를 시작한 사용자 ID
     */
    private String userId;

    /**
     * 고유 백테스트 식별자
     */
    private String backtestId;

    /**
     * 테스트한 종목 심볼 (예: "AAPL", "GOOGL")
     */
    private String symbol;

    /**
     * 백테스트 시작일
     */
    private LocalDate startDate;

    /**
     * 백테스트 종료일
     */
    private LocalDate endDate;

    /**
     * 초기 투자 금액
     */
    private BigDecimal initialInvestment;

    /**
     * 최종 포트폴리오 가치
     */
    private BigDecimal finalValue;

    /**
     * 총 수익 금액
     */
    private BigDecimal totalReturn;

    /**
     * 수익률 (퍼센트)
     */
    private BigDecimal returnPercentage;

    /**
     * 사용된 투자 전략 (예: "DCA", "LUMP_SUM")
     */
    private String strategyType;

    /**
     * 투자 모드 (예: "MONTHLY", "WEEKLY")
     */
    private String investmentMode;

    /**
     * 백테스트 중 실행된 거래 횟수
     */
    private Integer numberOfTrades;

    /**
     * 최대 손실률 (퍼센트)
     */
    private BigDecimal maxDrawdown;

    /**
     * 벤치마크 대비 비교 결과 (예: S&P 500)
     */
    private BigDecimal benchmarkComparison;

    /**
     * 백테스트 성공 여부
     */
    private Boolean successful;

    /**
     * 백테스트 실패 시 에러 메시지
     */
    private String errorMessage;

    /**
     * 실행 시간 (밀리초)
     */
    private Long executionTimeMs;
}
