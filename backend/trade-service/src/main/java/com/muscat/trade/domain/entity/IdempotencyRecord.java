package com.muscat.trade.domain.entity;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.Table;
import jakarta.persistence.UniqueConstraint;
import java.time.LocalDateTime;
import lombok.AccessLevel;
import lombok.Getter;
import lombok.NoArgsConstructor;

// 매수 · 매도에 실려 온 중복 요청 키(사용자 · 키마다 한 행, 처음 주문의 지문 · 체결 ID)
@Entity
@Table(name = "idempotency_keys", uniqueConstraints = @UniqueConstraint(
  name = "uk_idempotency_keys_user_key", columnNames = {"user_id", "idempotency_key"}))
@Getter
@NoArgsConstructor(access = AccessLevel.PROTECTED)
public class IdempotencyRecord {

  @Id
  @GeneratedValue(strategy = GenerationType.IDENTITY)
  private Long id;

  @Column(name = "user_id", nullable = false)
  private String userId;

  @Column(name = "idempotency_key", nullable = false, length = 64)
  private String idempotencyKey;

  @Column(nullable = false, length = 64)
  private String fingerprint;

  @Column(name = "trade_id")
  private Long tradeId; // 같은 트랜잭션에서 체결을 저장한 뒤 채움

  @Column(nullable = false)
  private LocalDateTime createdAt;

  public static IdempotencyRecord of(String userId, String idempotencyKey, String fingerprint, LocalDateTime createdAt) {
    IdempotencyRecord record = new IdempotencyRecord();
    record.userId = userId;
    record.idempotencyKey = idempotencyKey;
    record.fingerprint = fingerprint;
    record.createdAt = createdAt;
    return record;
  }

  public void attachTrade(Long tradeId) {
    this.tradeId = tradeId;
  }
}
