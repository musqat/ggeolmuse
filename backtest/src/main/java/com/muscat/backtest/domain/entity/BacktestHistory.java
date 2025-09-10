package com.muscat.backtest.domain.entity;

import jakarta.persistence.*;
import lombok.*;
import org.hibernate.annotations.CreationTimestamp;

import java.time.LocalDateTime;

@Entity
@Table(name = "backtest_history")
@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class BacktestHistory {

    @Id
    @GeneratedValue(strategy = GenerationType.UUID)
    private String backtestId;

    @Column(nullable = false)
    private String userId; // 사용자 ID

    @Column(nullable = false)
    @Enumerated(EnumType.STRING)
    private BacktestType backtestType; // STRATEGY, SYMBOL, TIMING, INVESTMENT

    @Column(length = 1000)
    private String requestParams; // 요청 파라미터 (JSON 형태)

    @CreationTimestamp
    @Column(nullable = false, updatable = false)
    private LocalDateTime createdAt; // 백테스팅 실행일시

    public enum BacktestType {
        STRATEGY_COMPARISON,    // 전략 비교 백테스트
        SYMBOL_COMPARISON,      // 종목 비교 백테스트  
        TIMING_COMPARISON,      // 타이밍 비교 백테스트
        INVESTMENT_BACKTEST     // 투자 내역 백테스트
    }
}