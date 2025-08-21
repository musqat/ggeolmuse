package com.muscat.user.domain.repository;

import com.muscat.user.domain.entity.User;
import com.muscat.user.common.enums.type.AuthProvider;
import java.util.Optional;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

@Repository
public interface UserRepository extends JpaRepository<User, Long> {

  // 이메일로 사용자 조회
  Optional<User> findByEmail(String email);

  // 이메일 중복 확인
  boolean existsByEmail(String email);

  // Keycloak ID로 사용자 조회 (로컬 사용자)
  Optional<User> findByKeycloakId(String keycloakId);

  // 소셜 ID와 프로바이더로 사용자 조회 (소셜 사용자)
  Optional<User> findBySocialIdAndProvider(String socialId, AuthProvider provider);

  // 닉네임 중복 확인
  boolean existsByNickname(String nickname);
}