package com.muscat.backtest.domain.entity;

import jakarta.persistence.*;
import lombok.*;
import org.hibernate.annotations.CreationTimestamp;
import org.hibernate.annotations.UpdateTimestamp;

import java.time.LocalDateTime;

@Entity
@Table(name = "investment_backtest_result")
@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class InvestmentBacktestResult {

    @Id
    @GeneratedValue(strategy = GenerationType.UUID)
    private String resultId;

    @Column(nullable = false, unique = true)
    private String userId; // 사용자 ID (유저당 하나의 최신 결과만 저장)

    @Column(columnDefinition = "TEXT")
    private String backtestResult; // 백테스트 결과 (JSON 형태)

    @Column(nullable = false)
    private LocalDateTime calculatedAt; // 계산 완료 시간

    @Column
    private LocalDateTime nextScheduledAt; // 다음 계산 예정 시간

    @Column
    private Long executionTimeMs; // 실행 시간 (밀리초)

    @Enumerated(EnumType.STRING)
    @Builder.Default
    private CalculationStatus status = CalculationStatus.SCHEDULED; // 계산 상태

    @CreationTimestamp
    @Column(nullable = false, updatable = false)
    private LocalDateTime createdAt;

    @UpdateTimestamp
    @Column(nullable = false)
    private LocalDateTime updatedAt;

    public enum CalculationStatus {
        SCHEDULED,  // 계산 예정
        RUNNING,    // 계산 중
        COMPLETED,  // 계산 완료
        FAILED      // 계산 실패
    }
}