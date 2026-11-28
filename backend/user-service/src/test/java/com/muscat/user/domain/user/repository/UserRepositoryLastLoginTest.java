package com.muscat.user.domain.user.repository;

import static org.assertj.core.api.Assertions.assertThat;

import com.muscat.jpatest.UserPostgresJpaTest;
import com.muscat.user.common.enums.type.AuthType;
import com.muscat.user.common.enums.type.UserRole;
import com.muscat.user.domain.user.entity.User;
import java.time.LocalDateTime;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.jpa.test.autoconfigure.TestEntityManager;
import org.springframework.transaction.annotation.Propagation;
import org.springframework.transaction.annotation.Transactional;

@UserPostgresJpaTest
@DisplayName("사용자 마지막 로그인 시각 갱신")
class UserRepositoryLastLoginTest {

  @Autowired
  private UserRepository userRepository;

  @Autowired
  private TestEntityManager entityManager;

  @Test
  @DisplayName("마지막 로그인 시각만 바꾸고 닉네임 같은 다른 칸은 그대로 둔다")
  void updateLastLoginAt_OnlyThatColumn() {
    User user = userRepository.saveAndFlush(User.builder()
      .email("login@example.com")
      .nickname("before")
      .keycloakId("kc-login")
      .provider(AuthType.LOCAL)
      .role(UserRole.USER)
      .emailVerified(true)
      .build());
    entityManager.clear();
    LocalDateTime at = LocalDateTime.of(2026, 11, 5, 9, 30);

    long updated = userRepository.updateLastLoginAt(user.getId(), at);
    entityManager.clear();

    User reloaded = userRepository.findById(user.getId()).orElseThrow();
    assertThat(updated).isEqualTo(1);
    assertThat(reloaded.getLastLoginAt()).isEqualTo(at);
    assertThat(reloaded.getNickname()).isEqualTo("before");
  }

  @Test
  @Transactional(propagation = Propagation.NOT_SUPPORTED)
  @DisplayName("트랜잭션 없이 불러도 저장한다")
  void updateLastLoginAt_WithoutCallerTransaction() {
    User user = userRepository.saveAndFlush(User.builder()
      .email("login-tx@example.com")
      .nickname("login-tx")
      .keycloakId("kc-login-tx")
      .provider(AuthType.LOCAL)
      .role(UserRole.USER)
      .emailVerified(true)
      .build());
    LocalDateTime at = LocalDateTime.of(2026, 11, 5, 9, 30);

    try {
      userRepository.updateLastLoginAt(user.getId(), at);

      assertThat(userRepository.findById(user.getId()).orElseThrow().getLastLoginAt()).isEqualTo(at);
    } finally {
      userRepository.deleteById(user.getId());
    }
  }

  @Test
  @DisplayName("없는 사용자면 바뀐 행이 없다")
  void updateLastLoginAt_UnknownUser() {
    assertThat(userRepository.updateLastLoginAt(999_999L, LocalDateTime.of(2026, 11, 5, 9, 30))).isZero();
  }
}
