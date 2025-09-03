package com.muscat.user.domain.user.entity;

import com.muscat.user.common.enums.type.AuthType;
import com.muscat.user.common.exceptions.UserException;
import com.muscat.user.common.responses.UserResponse;
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
import java.math.BigDecimal;
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
  private String passwordHash; // Primary password storage 

  private String nickname;

  private String keycloakId; // Keycloak 연동용

  @Enumerated(EnumType.STRING)
  @Builder.Default
  private AuthType provider = AuthType.LOCAL;

  @Builder.Default
  private boolean emailVerified = false;

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


  // 계정 삭제 전 검증 메서드
  public void validateForDeletion() {
    for (Account account : accounts) {
      if (account.getBalanceKrw().compareTo(BigDecimal.ZERO) > 0 ||
          account.getBalanceUsd().compareTo(BigDecimal.ZERO) > 0) {
        throw new UserException(UserResponse.ACCOUNT_DELETION_BLOCKED,
            String.format("계좌 '%s'에 잔액이 있습니다. (KRW: %s, USD: %s)",
                account.getAccountName(), account.getBalanceKrw(), account.getBalanceUsd()));
      }
    }
  }

}