package com.muscat.user.domain.user.repository;

import com.muscat.user.common.enums.type.UserRole;
import com.muscat.user.domain.user.entity.User;
import java.time.LocalDateTime;
import java.util.Optional;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;
import org.springframework.transaction.annotation.Transactional;

@Repository
public interface UserRepository extends JpaRepository<User, Long>, UserRepositoryCustom {

  // 이메일로 사용자 조회
  Optional<User> findByEmail(String email);

  // 이메일 중복 확인
  boolean existsByEmail(String email);

  // Keycloak ID로 사용자 조회
  Optional<User> findByKeycloakId(String keycloakId);

  // 닉네임 중복 확인
  boolean existsByNickname(String nickname);

  // 마지막 로그인 시각 한 칸만 갱신
  @Transactional
  @Modifying
  @Query("update User u set u.lastLoginAt = :at where u.id = :id")
  int updateLastLoginAt(@Param("id") Long id, @Param("at") LocalDateTime at);

  // ==================== ADMIN API ====================

  // 활성화된 사용자 수
  long countByEnabled(boolean enabled);

  // 특정 역할의 사용자 수
  long countByRole(UserRole role);
}
