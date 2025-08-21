package com.muscat.user.domain.repository;

import com.muscat.user.domain.entity.EmailToken;
import com.muscat.user.domain.entity.User;
import java.time.LocalDateTime;
import java.util.Optional;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

@Repository
public interface EmailTokenRepository extends JpaRepository<EmailToken, Long> {

  Optional<EmailToken> findByToken(String token);

  Optional<EmailToken> findByUser(User user);

  // 만료된 토큰들 정리용
  void deleteByExpiryDateBefore(LocalDateTime dateTime);

  // 사용자별 기존 토큰 삭제용 (새 토큰 발급 시)
  void deleteByUser(User user);
}
