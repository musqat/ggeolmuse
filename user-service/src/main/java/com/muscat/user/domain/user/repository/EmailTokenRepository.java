package com.muscat.user.domain.user.repository;

import com.muscat.user.domain.user.entity.EmailToken;
import com.muscat.user.domain.user.entity.User;
import java.util.Optional;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;

@Repository
public interface EmailTokenRepository extends JpaRepository<EmailToken, Long> {

  // 토큰으로 인증 토큰 조회
  Optional<EmailToken> findByToken(String token);

  // 토큰으로 인증 토큰 조회 (User 함께 로드)
  @Query("SELECT e FROM EmailToken e JOIN FETCH e.user WHERE e.token = :token")
  Optional<EmailToken> findByTokenWithUser(@Param("token") String token);

  // 사용자별 토큰 전체 삭제
  void deleteByUser(User user);
}
