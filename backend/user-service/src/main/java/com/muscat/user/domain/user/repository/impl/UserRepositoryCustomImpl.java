package com.muscat.user.domain.user.repository.impl;

import com.muscat.user.domain.user.entity.QUser;
import com.muscat.user.domain.user.entity.User;
import com.muscat.user.domain.user.repository.UserRepositoryCustom;
import com.querydsl.jpa.impl.JPAQueryFactory;
import java.util.Optional;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Repository;

@Repository
@RequiredArgsConstructor
public class UserRepositoryCustomImpl implements UserRepositoryCustom {

  private final JPAQueryFactory queryFactory;
  private static final QUser user = QUser.user;

  @Override
  public Optional<User> findByEmailWithAccounts(String email) {
    User result = queryFactory
      .selectFrom(user)
      .leftJoin(user.accounts).fetchJoin()
      .where(user.email.eq(email))
      .fetchOne();
    return Optional.ofNullable(result);
  }
}
