package com.muscat.backtest.domain.entity;

import com.muscat.backtest.common.enums.type.BacktestType;
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
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(nullable = true)
    private String userId; // 사용자 ID (null 가능 - 비로그인 사용자)

    @Column(nullable = false)
    @Enumerated(EnumType.STRING)
    private BacktestType backtestType; // STRATEGY, SYMBOL, TIMING, INVESTMENT

    @Column(length = 2000)
    private String requestParams; // 요청 파라미터 (JSON 형태)

    @Column(length = 20)
    private String fxRateMode; // 환율 설정 모드: "auto" 또는 "manual"

    @CreationTimestamp
    @Column(nullable = false, updatable = false)
    private LocalDateTime createdAt; // 백테스팅 실행일시

}