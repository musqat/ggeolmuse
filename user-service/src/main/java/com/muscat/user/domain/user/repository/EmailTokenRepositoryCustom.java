package com.muscat.user.domain.user.repository;

import com.muscat.user.domain.user.entity.EmailToken;
import java.util.Optional;

public interface EmailTokenRepositoryCustom {

  // 토큰으로 인증 토큰 조회 (User 함께 로드)
  Optional<EmailToken> findByTokenWithUser(String token);
}
