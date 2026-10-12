package com.muscat.user.domain.account.repository.impl;

import com.muscat.user.domain.account.entity.Account;
import com.muscat.user.domain.account.entity.QAccount;
import com.muscat.user.domain.account.repository.AccountRepositoryCustom;
import com.muscat.user.domain.user.entity.QUser;
import com.querydsl.jpa.impl.JPAQueryFactory;
import java.util.List;
import java.util.Optional;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Repository;

/**
 * AccountRepositoryCustom 구현체
 */
@Repository
@RequiredArgsConstructor
public class AccountRepositoryCustomImpl implements AccountRepositoryCustom {

  private final JPAQueryFactory queryFactory;
  private static final QAccount account = QAccount.account;
  private static final QUser user = QUser.user;

  @Override
  public List<Account> findByUserIdWithUser(Long userId) {
    return queryFactory
      .selectFrom(account)
      .join(account.user, user).fetchJoin()
      .where(account.user.id.eq(userId))
      .fetch();
  }

  @Override
  public Optional<Account> findByIdAndUserId(Long accountId, Long userId) {
    Account result = queryFactory
      .selectFrom(account)
      .join(account.user, user).fetchJoin()
      .where(
        account.id.eq(accountId),
        account.user.id.eq(userId)
      )
      .fetchOne();
    return Optional.ofNullable(result);
  }

  @Override
  public boolean existsByUserIdAndAccountName(Long userId, String accountName) {
    Integer result = queryFactory
      .selectOne()
      .from(account)
      .where(
        account.user.id.eq(userId),
        account.accountName.eq(accountName)
      )
      .fetchFirst();
    return result != null;
  }

  @Override
  public Optional<Account> findByIdAndUserIdWithLock(Long accountId, Long userId) {
    Account result = queryFactory
      .selectFrom(account)
      .join(account.user, user).fetchJoin()  // N+1 방지
      .where(
        account.id.eq(accountId),
        account.user.id.eq(userId)
      )
      .setLockMode(jakarta.persistence.LockModeType.PESSIMISTIC_WRITE)
      .fetchOne();
    return Optional.ofNullable(result);
  }

  @Override
  public Optional<Account> findByAccountNumber(String accountNumber) {
    Account result = queryFactory
      .selectFrom(account)
      .join(account.user, user).fetchJoin()  // N+1 방지
      .where(account.accountNumber.eq(accountNumber))
      .fetchOne();
    return Optional.ofNullable(result);
  }

  @Override
  public Optional<Account> findByIdWithLock(Long accountId) {
    Account result = queryFactory
      .selectFrom(account)
      .join(account.user, user).fetchJoin()  // N+1 방지
      .where(account.id.eq(accountId))
      .setLockMode(jakarta.persistence.LockModeType.PESSIMISTIC_WRITE)
      .fetchOne();
    return Optional.ofNullable(result);
  }

}
