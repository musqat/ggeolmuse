package com.muscat.user.domain.user.repository.impl;

import com.muscat.user.domain.user.entity.PasswordResetToken;
import com.muscat.user.domain.user.entity.QPasswordResetToken;
import com.muscat.user.domain.user.entity.QUser;
import com.muscat.user.domain.user.repository.PasswordResetTokenRepositoryCustom;
import com.querydsl.jpa.impl.JPAQueryFactory;
import java.util.Optional;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Repository;

@Repository
@RequiredArgsConstructor
public class PasswordResetTokenRepositoryCustomImpl implements PasswordResetTokenRepositoryCustom {

  private final JPAQueryFactory queryFactory;
  private static final QPasswordResetToken passwordResetToken = QPasswordResetToken.passwordResetToken;
  private static final QUser user = QUser.user;

  @Override
  public Optional<PasswordResetToken> findByTokenWithUser(String token) {
    PasswordResetToken result = queryFactory
      .selectFrom(passwordResetToken)
      .join(passwordResetToken.user, user).fetchJoin()
      .where(passwordResetToken.token.eq(token))
      .fetchOne();
    return Optional.ofNullable(result);
  }
}
