package com.muscat.user.domain.user.entity;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.FetchType;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.JoinColumn;
import jakarta.persistence.ManyToOne;
import jakarta.persistence.Table;
import java.time.LocalDateTime;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;
import org.hibernate.annotations.CreationTimestamp;

/**
 * 비밀번호 재설정 토큰 엔티티
 * 토큰 유효기간: 30분
 */
@Entity
@Table(name = "password_reset_tokens")
@Getter
@Setter
@NoArgsConstructor
public class PasswordResetToken {
  @Id
  @GeneratedValue(strategy = GenerationType.IDENTITY)
  private Long id; // PK

  @Column(nullable = false, unique = true)
  private String token; // 비밀번호 재설정 토큰

  @ManyToOne(fetch = FetchType.LAZY)
  @JoinColumn(name = "user_id", nullable = false)
  private User user; // 연결된 사용자

  @CreationTimestamp
  private LocalDateTime createdAt; // 생성일시

  @Column(nullable = false)
  private LocalDateTime expiryDate; // 만료일시 (30분)

  @Column(nullable = false)
  private boolean used = false; // 사용 여부 (한 번 사용하면 재사용 불가)

  /**
   * 토큰 만료 체크
   * @return 만료 여부
   */
  public boolean isExpired() {
    return LocalDateTime.now().isAfter(expiryDate);
  }

  /**
   * 토큰 유효성 검증 (만료 + 사용 여부)
   */
  public boolean isValid() {
    return !isExpired() && !used;
  }

  /**
   * 토큰 사용 처리
   */
  public void markAsUsed() {
    this.used = true;
  }
}
