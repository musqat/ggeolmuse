package com.muscat.user.domain.user.repository;

import com.muscat.user.domain.user.entity.User;
import java.time.LocalDateTime;
import java.util.Optional;

public interface UserRepositoryCustom {

  // Keycloak ID 로 사용자 조회 (계정 정보 포함)
  Optional<User> findByKeycloakIdWithAccounts(String keycloakId);

  // 마지막 로그인 시각 한 칸만 갱신(바꾼 행 수)
  long updateLastLoginAt(Long id, LocalDateTime at);
}
