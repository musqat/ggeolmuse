package com.muscat.backtest.domain.entity;

import jakarta.persistence.*;
import lombok.*;
import org.hibernate.annotations.CreationTimestamp;
import org.hibernate.annotations.UpdateTimestamp;

import java.time.LocalDateTime;

@Entity
@Table(name = "investment_backtest_result")
@Getter
@NoArgsConstructor
@AllArgsConstructor
@Builder(toBuilder = true)
public class InvestmentBacktestResult {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(nullable = false, unique = true)
    private String userId;

    @Column(columnDefinition = "TEXT")
    private String resultData;

    @Column(nullable = false)
    private LocalDateTime calculatedAt;

    @CreationTimestamp
    @Column(nullable = false, updatable = false)
    private LocalDateTime createdAt;

    @UpdateTimestamp
    @Column(nullable = false)
    private LocalDateTime updatedAt;

    public InvestmentBacktestResult updateResult(String resultData, LocalDateTime calculatedAt) {
        return this.toBuilder()
            .resultData(resultData)
            .calculatedAt(calculatedAt)
            .build();
    }
}