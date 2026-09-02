package com.muscat.user.domain.user.repository.impl;

import com.muscat.user.domain.user.entity.EmailToken;
import com.muscat.user.domain.user.entity.QEmailToken;
import com.muscat.user.domain.user.entity.QUser;
import com.muscat.user.domain.user.repository.EmailTokenRepositoryCustom;
import com.querydsl.jpa.impl.JPAQueryFactory;
import java.util.Optional;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Repository;

@Repository
@RequiredArgsConstructor
public class EmailTokenRepositoryCustomImpl implements EmailTokenRepositoryCustom {

  private final JPAQueryFactory queryFactory;
  private static final QEmailToken emailToken = QEmailToken.emailToken;
  private static final QUser user = QUser.user;

  @Override
  public Optional<EmailToken> findByTokenWithUser(String token) {
    EmailToken result = queryFactory
      .selectFrom(emailToken)
      .join(emailToken.user, user).fetchJoin()
      .where(emailToken.token.eq(token))
      .fetchOne();
    return Optional.ofNullable(result);
  }
}
