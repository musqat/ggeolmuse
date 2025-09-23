package com.muscat.user.domain.account.repository;

import com.muscat.user.common.enums.type.TransactionType;
import com.muscat.user.domain.account.entity.Account;
import com.muscat.user.domain.account.entity.AccountHistory;
import com.muscat.user.domain.account.entity.QAccount;
import com.muscat.user.domain.account.entity.QAccountHistory;
import com.muscat.user.domain.user.entity.QUser;
import com.querydsl.jpa.impl.JPAQueryFactory;
import lombok.RequiredArgsConstructor;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageImpl;
import org.springframework.data.domain.Pageable;
import org.springframework.stereotype.Repository;

import java.math.BigDecimal;
import java.time.LocalDateTime;
import java.util.List;
import java.util.Optional;

@Repository
@RequiredArgsConstructor
public class AccountQueryRepository {

    private final JPAQueryFactory queryFactory;
    private static final QAccount account = QAccount.account;
    private static final QUser user = QUser.user;
    private static final QAccountHistory accountHistory = QAccountHistory.accountHistory;

    // JOIN FETCH로 User와 함께 Account 조회 - N+1 문제 방지
    public List<Account> findByUserIdWithUser(Long userId) {
        return queryFactory
            .selectFrom(account)
            .join(account.user, user).fetchJoin()
            .where(account.user.id.eq(userId))
            .fetch();
    }

    public Optional<Account> findByIdAndUserId(Long accountId, Long userId) {
        Account result = queryFactory
            .selectFrom(account)
            .where(
                account.id.eq(accountId),
                account.user.id.eq(userId)
            )
            .fetchOne();
        return Optional.ofNullable(result);
    }

    // 계좌 ID로 단순 조회
    public Optional<Account> findById(Long accountId) {
        Account result = queryFactory
            .selectFrom(account)
            .where(account.id.eq(accountId))
            .fetchOne();
        return Optional.ofNullable(result);
    }

    // 통화별 계좌 거래 내역 조회 (페이지네이션)
    public Page<AccountHistory> findByAccountAndCurrencyOrderByCreatedAtDesc(
            Account targetAccount, String currency, Pageable pageable) {

        // 메인 쿼리
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

        // 카운트 쿼리
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

    // 기간별 계좌 거래 내역 조회 (페이지네이션)
    public Page<AccountHistory> findByAccountAndRangeOrderByCreatedAtDesc(
            Account targetAccount, LocalDateTime from, LocalDateTime to, Pageable pageable) {

        // 메인 쿼리
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

        // 카운트 쿼리
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

    // 특정 거래 내역 조회 (권한 체크 포함)
    public Optional<AccountHistory> findByIdAndAccountId(Long historyId, Long accountId) {
        AccountHistory result = queryFactory
            .selectFrom(accountHistory)
            .where(
                accountHistory.id.eq(historyId),
                accountHistory.account.id.eq(accountId)
            )
            .fetchOne();
        return Optional.ofNullable(result);
    }

    // 환전 내역만 조회
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

    // 거래 유형별 총액 계산 (통계)
    public BigDecimal getTotalAmountByAccountAndType(Account targetAccount, TransactionType transactionType) {
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

    // 중복 거래 방지용 - 참조 ID 존재 여부 확인
    public boolean existsByReferenceId(String referenceId) {
        return queryFactory
            .selectOne()
            .from(accountHistory)
            .where(accountHistory.referenceId.eq(referenceId))
            .fetchFirst() != null;
    }

    // 사용자 ID와 계좌명으로 중복 확인
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

    // 락을 걸고 계좌 조회 (사용자 ID와 계좌 ID로)
    public Optional<Account> findByIdAndUserIdWithLock(Long accountId, Long userId) {
        Account result = queryFactory
            .selectFrom(account)
            .where(
                account.id.eq(accountId),
                account.user.id.eq(userId)
            )
            .setLockMode(jakarta.persistence.LockModeType.PESSIMISTIC_WRITE)
            .fetchOne();
        return Optional.ofNullable(result);
    }

    // 계좌번호로 계좌 조회
    public Optional<Account> findByAccountNumber(String accountNumber) {
        Account result = queryFactory
            .selectFrom(account)
            .where(account.accountNumber.eq(accountNumber))
            .fetchOne();
        return Optional.ofNullable(result);
    }

    // 락을 걸고 계좌 조회 (계좌 ID만으로)
    public Optional<Account> findByIdWithLock(Long accountId) {
        Account result = queryFactory
            .selectFrom(account)
            .where(account.id.eq(accountId))
            .setLockMode(jakarta.persistence.LockModeType.PESSIMISTIC_WRITE)
            .fetchOne();
        return Optional.ofNullable(result);
    }
}