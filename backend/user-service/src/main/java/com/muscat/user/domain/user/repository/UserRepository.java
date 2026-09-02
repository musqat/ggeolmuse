package com.muscat.user.domain.user.repository;

import com.muscat.user.common.enums.type.AuthType;
import com.muscat.user.common.enums.type.UserRole;
import com.muscat.user.domain.user.entity.User;
import java.util.Optional;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

@Repository
public interface UserRepository extends JpaRepository<User, Long>, UserRepositoryCustom {

  // 이메일로 사용자 조회
  Optional<User> findByEmail(String email);

  // 이메일 중복 확인
  boolean existsByEmail(String email);

  // Keycloak ID로 사용자 조회
  Optional<User> findByKeycloakId(String keycloakId);

  // 소셜 ID와 프로바이더로 사용자 조회
  Optional<User> findBySocialIdAndProvider(String socialId, AuthType provider);

  // 닉네임 중복 확인
  boolean existsByNickname(String nickname);

  // ==================== ADMIN API ====================

  // 활성화된 사용자 수
  long countByEnabled(boolean enabled);

  // 특정 역할의 사용자 수
  long countByRole(UserRole role);
}
