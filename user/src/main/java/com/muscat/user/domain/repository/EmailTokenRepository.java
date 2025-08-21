package com.muscat.user.domain.repository;

import com.muscat.user.domain.entity.EmailToken;
import com.muscat.user.domain.entity.User;
import java.util.Optional;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

@Repository
public interface EmailTokenRepository extends JpaRepository<EmailToken, Long> {

  //토큰 문자열로 이메일 인증 토큰 조회
  Optional<EmailToken> findByToken(String token);

  //사용자별 기존 토큰 삭제 새 토큰 발급 시 중복 방지를 위해 기존 토큰들을 모두 삭제
  void deleteByUser(User user);
}