package com.muscat.user.domain.entity;

import com.muscat.user.common.enums.type.AuthProvider;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.Table;
import java.time.LocalDateTime;
import lombok.Builder;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.AllArgsConstructor;
import lombok.Setter;
import org.hibernate.annotations.CreationTimestamp;
import org.hibernate.annotations.UpdateTimestamp;

@Entity
@Table(name = "users")
@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class User {
  @Id
  @GeneratedValue(strategy = GenerationType.IDENTITY)
  private Long id;

  @Column(unique = true, nullable = false)
  private String email;

  private String password; // 자체 회원가입용

  private String nickname;

  private String keycloakId; // Keycloak 연동용

  @Enumerated(EnumType.STRING)
  @Builder.Default
  private AuthProvider provider = AuthProvider.LOCAL;

  @Builder.Default
  private boolean emailVerified = false;

  @CreationTimestamp
  private LocalDateTime createdAt;

  @UpdateTimestamp
  private LocalDateTime updatedAt;

  @Column(name = "social_id")
  private String socialId; // Google의 sub 클레임 값

  @Column(name = "social_email")
  private String socialEmail; // Google 계정 이메일 (email과 다를 수 있음)

  @Column(name = "profile_image_url")
  private String profileImageUrl; // Google 프로필 이미지


}