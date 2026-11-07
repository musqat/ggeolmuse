package com.muscat.user.domain.account.entity;

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

// 입금 · 환전에 실려 온 중복 요청 키(사용자 · 키마다 한 행, 처음 요청의 지문)
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
  private Long userId;

  @Column(name = "idempotency_key", nullable = false, length = 64)
  private String idempotencyKey;

  @Column(nullable = false, length = 64)
  private String fingerprint;

  @Column(nullable = false)
  private LocalDateTime createdAt;

  public static IdempotencyRecord of(Long userId, String idempotencyKey, String fingerprint, LocalDateTime createdAt) {
    IdempotencyRecord record = new IdempotencyRecord();
    record.userId = userId;
    record.idempotencyKey = idempotencyKey;
    record.fingerprint = fingerprint;
    record.createdAt = createdAt;
    return record;
  }
}
