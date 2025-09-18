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

@Entity
@Table(name = "email_verification_tokens")
@Getter
@Setter
@NoArgsConstructor
public class EmailToken {
  @Id
  @GeneratedValue(strategy = GenerationType.IDENTITY)
  private Long id; // PK

  @Column(nullable = false, unique = true)
  private String token; // 이메일 인증 토큰

  @ManyToOne(fetch = FetchType.LAZY)
  @JoinColumn(name = "user_id", nullable = false)
  private User user; // 연결된 사용자

  @CreationTimestamp
  private LocalDateTime createdAt; // 생성일시

  @Column(nullable = false)
  private LocalDateTime expiryDate; // 만료일시

  // 토큰 만료 체크용
  public boolean isExpired() {
    return LocalDateTime.now().isAfter(expiryDate);
  }
}
