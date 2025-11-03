package com.muscat.user.domain.user.repository;

import com.muscat.user.domain.user.entity.PasswordResetToken;
import com.muscat.user.domain.user.entity.User;
import java.util.Optional;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

/**
 * 비밀번호 재설정 토큰 Repository
 */
@Repository
public interface PasswordResetTokenRepository extends JpaRepository<PasswordResetToken, Long> {

  /**
   * 토큰으로 비밀번호 재설정 토큰 조회
   */
  Optional<PasswordResetToken> findByToken(String token);

  /**
   * 사용자별 비밀번호 재설정 토큰 조회
   */
  Optional<PasswordResetToken> findByUser(User user);

  /**
   * 사용자별 토큰 전체 삭제
   */
  void deleteByUser(User user);
}
