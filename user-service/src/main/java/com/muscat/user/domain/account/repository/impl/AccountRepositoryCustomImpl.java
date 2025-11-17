package com.muscat.user.domain.account.repository.impl;

import com.muscat.user.common.enums.type.TransactionType;
import com.muscat.user.domain.account.entity.Account;
import com.muscat.user.domain.account.entity.AccountHistory;
import com.muscat.user.domain.account.entity.QAccount;
import com.muscat.user.domain.account.entity.QAccountHistory;
import com.muscat.user.domain.account.repository.AccountRepositoryCustom;
import com.muscat.user.domain.user.entity.QUser;
import com.querydsl.jpa.impl.JPAQueryFactory;
import java.math.BigDecimal;
import java.time.LocalDateTime;
import java.util.List;
import java.util.Optional;
import lombok.RequiredArgsConstructor;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageImpl;
import org.springframework.data.domain.Pageable;
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
  private static final QAccountHistory accountHistory = QAccountHistory.accountHistory;

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

  @Override
  public Optional<AccountHistory> findHistoryByIdAndAccountId(Long historyId, Long accountId) {
    AccountHistory result = queryFactory
      .selectFrom(accountHistory)
      .where(
        accountHistory.id.eq(historyId),
        accountHistory.account.id.eq(accountId)
      )
      .fetchOne();
    return Optional.ofNullable(result);
  }

  @Override
  public Page<AccountHistory> findHistoryByAccountAndCurrency(
    Account targetAccount, String currency, Pageable pageable) {

    List<AccountHistory> content = queryFactory
      .selectFrom(accountHistory)
      .where(
        accountHistory.account.eq(targetAccount),
        accountHistory.currency.eq(currency)
      )
      .orderBy(accountHistory.createdAt.desc(), accountHistory.id.desc())
      .offset(pageable.getOffset())
      .limit(pageable.getPageSize())
      .fetch();

    Long total = queryFactory
      .select(accountHistory.count())
      .from(accountHistory)
      .where(
        accountHistory.account.eq(targetAccount),
        accountHistory.currency.eq(currency)
      )
      .fetchOne();

    return new PageImpl<>(content, pageable, total != null ? total : 0L);
  }

  @Override
  public Page<AccountHistory> findHistoryByAccountAndRange(
    Account targetAccount, LocalDateTime from, LocalDateTime to, Pageable pageable) {

    List<AccountHistory> content = queryFactory
      .selectFrom(accountHistory)
      .where(
        accountHistory.account.eq(targetAccount),
        from != null ? accountHistory.createdAt.goe(from) : null,
        to != null ? accountHistory.createdAt.lt(to) : null
      )
      .orderBy(accountHistory.createdAt.desc(), accountHistory.id.desc())
      .offset(pageable.getOffset())
      .limit(pageable.getPageSize())
      .fetch();

    Long total = queryFactory
      .select(accountHistory.count())
      .from(accountHistory)
      .where(
        accountHistory.account.eq(targetAccount),
        from != null ? accountHistory.createdAt.goe(from) : null,
        to != null ? accountHistory.createdAt.lt(to) : null
      )
      .fetchOne();

    return new PageImpl<>(content, pageable, total != null ? total : 0L);
  }

  @Override
  public List<AccountHistory> findExchangeHistoryByAccount(Account targetAccount) {
    return queryFactory
      .selectFrom(accountHistory)
      .where(
        accountHistory.account.eq(targetAccount),
        accountHistory.fromCurrency.isNotNull(),
        accountHistory.toCurrency.isNotNull()
      )
      .orderBy(accountHistory.id.desc())
      .fetch();
  }

  @Override
  public BigDecimal getTotalAmountByAccountAndType(Account targetAccount,
    TransactionType transactionType) {
    BigDecimal result = queryFactory
      .select(accountHistory.amount.sum())
      .from(accountHistory)
      .where(
        accountHistory.account.eq(targetAccount),
        accountHistory.transactionType.eq(transactionType)
      )
      .fetchOne();
    return result != null ? result : BigDecimal.ZERO;
  }
}
