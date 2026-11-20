package com.muscat.user.domain.user.repository;

import com.muscat.user.domain.user.entity.UserDeletion;
import java.time.LocalDateTime;
import java.util.List;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.transaction.annotation.Transactional;

public interface UserDeletionRepository extends JpaRepository<UserDeletion, String> {

  // 이벤트 발행이나 Keycloak 삭제가 남은 기록
  List<UserDeletion> findByEventPublishedAtIsNullOrKeycloakDeletedAtIsNull();

  // 둘 다 끝나고 cutoff 전에 Keycloak 삭제까지 끝난 기록 지우기
  @Transactional
  long deleteByEventPublishedAtIsNotNullAndKeycloakDeletedAtBefore(LocalDateTime cutoff);
}
