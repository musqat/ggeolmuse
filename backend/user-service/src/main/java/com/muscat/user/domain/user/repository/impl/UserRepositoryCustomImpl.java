package com.muscat.user.domain.user.repository.impl;

import com.muscat.user.domain.user.entity.QUser;
import com.muscat.user.domain.user.entity.User;
import com.muscat.user.domain.user.repository.UserRepositoryCustom;
import com.querydsl.jpa.impl.JPAQueryFactory;
import java.time.LocalDateTime;
import java.util.Optional;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Repository;
import org.springframework.transaction.annotation.Transactional;

@Repository
@RequiredArgsConstructor
public class UserRepositoryCustomImpl implements UserRepositoryCustom {

  private final JPAQueryFactory queryFactory;
  private static final QUser user = QUser.user;

  @Override
  public Optional<User> findByKeycloakIdWithAccounts(String keycloakId) {
    User result = queryFactory
      .selectFrom(user)
      .leftJoin(user.accounts).fetchJoin()
      .where(user.keycloakId.eq(keycloakId))
      .fetchOne();
    return Optional.ofNullable(result);
  }

  @Override
  @Transactional
  public long updateLastLoginAt(Long id, LocalDateTime at) {
    return queryFactory
      .update(user)
      .set(user.lastLoginAt, at)
      .where(user.id.eq(id))
      .execute();
  }
}
