package com.muscat.user.domain.user.entity;

import com.muscat.user.common.enums.type.AuthType;
import com.muscat.user.common.enums.type.UserRole;
import com.muscat.user.domain.account.entity.Account;
import jakarta.persistence.CascadeType;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.OneToMany;
import jakarta.persistence.Table;
import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.List;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Getter;
import lombok.NoArgsConstructor;
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

  @Column(name = "password_hash")
  private String passwordHash; // Primary password

  // 사용자마다 다름(대소문자 구분), 비면 아직 안 정함
  @Column(unique = true)
  private String nickname;

  @Column(nullable = false, unique = true)
  private String keycloakId; // Keycloak 연동용

  @Enumerated(EnumType.STRING)
  @Builder.Default
  private AuthType provider = AuthType.LOCAL;

  @Enumerated(EnumType.STRING)
  @Builder.Default
  @Column(nullable = false)
  private UserRole role = UserRole.USER;

  @Builder.Default
  private boolean emailVerified = false;

  @Builder.Default
  @Column(nullable = false)
  private boolean enabled = true; // 계정 활성화 여부 (관리자가 관리)

  @Column(name = "last_login_at")
  private LocalDateTime lastLoginAt; // 마지막 로그인 시간

  @CreationTimestamp
  private LocalDateTime createdAt;

  @UpdateTimestamp
  private LocalDateTime updatedAt;

  @Column(name = "social_id")
  private String socialId; // Google의 sub 클레임 값

  @Column(name = "social_email")
  private String socialEmail; // Google 계정 이메일

  @Column(name = "profile_image_url")
  private String profileImageUrl; // Google 프로필 이미지

  @OneToMany(mappedBy = "user", cascade = CascadeType.ALL, orphanRemoval = true)
  @Builder.Default
  private List<Account> accounts = new ArrayList<>();


  // ============================================
  // 도메인 메서드 (Domain Methods)
  // ============================================

  // 비밀번호 변경
  public void changePassword(String encodedPassword) {
    this.passwordHash = encodedPassword;
  }

  // 이메일 인증 완료
  public void verifyEmail() {
    this.emailVerified = true;
  }

  // 닉네임 변경
  public void changeNickname(String newNickname) {
    this.nickname = newNickname;
  }

  // 이메일 변경
  public void changeEmail(String newEmail) {
    this.email = newEmail;
  }

  // 역할 변경 (관리자용)
  public void changeRole(UserRole newRole) {
    this.role = newRole;
  }

  // 계정 활성화/비활성화 (관리자용)
  public void changeEnabledStatus(boolean enabled) {
    this.enabled = enabled;
  }

}
