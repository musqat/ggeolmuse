package com.muscat.backtest.domain.entity;

import jakarta.persistence.*;
import lombok.*;
import org.hibernate.annotations.CreationTimestamp;
import org.hibernate.annotations.UpdateTimestamp;

import java.time.LocalDateTime;
import java.util.UUID;

@Entity
@Table(name = "investment_backtest_result")
@Getter
@NoArgsConstructor
@AllArgsConstructor
@Builder(toBuilder = true)
public class InvestmentBacktestResult {

    @Id
    private String resultId;

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

    @PrePersist
    private void generateId() {
        if (this.resultId == null) {
            this.resultId = UUID.randomUUID().toString();
        }
    }

    public InvestmentBacktestResult updateResult(String resultData, LocalDateTime calculatedAt) {
        return this.toBuilder()
            .resultData(resultData)
            .calculatedAt(calculatedAt)
            .build();
    }
}