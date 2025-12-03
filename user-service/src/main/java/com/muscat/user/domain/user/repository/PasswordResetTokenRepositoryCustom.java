package com.muscat.user.domain.user.repository;

import com.muscat.user.domain.user.entity.PasswordResetToken;
import java.util.Optional;

public interface PasswordResetTokenRepositoryCustom {

  // 토큰으로 비밀번호 재설정 토큰 조회 (User 함께 로드)
  Optional<PasswordResetToken> findByTokenWithUser(String token);
}
