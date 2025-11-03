package com.muscat.user.domain.account.repository;

import com.muscat.user.common.enums.type.TransactionType;
import com.muscat.user.domain.account.entity.Account;
import com.muscat.user.domain.account.entity.AccountHistory;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;

import java.math.BigDecimal;
import java.time.LocalDateTime;
import java.util.List;
import java.util.Optional;

/**
 * Account Repository Custom Interface
 */
public interface AccountRepositoryCustom {

    /**
     * JOIN FETCH로 User와 함께 Account 조회 - N+1 문제 방지
     */
    List<Account> findByUserIdWithUser(Long userId);

    /**
     * 계좌 ID와 사용자 ID로 조회
     */
    Optional<Account> findByIdAndUserId(Long accountId, Long userId);

    /**
     * 사용자 ID와 계좌명으로 중복 확인
     */
    boolean existsByUserIdAndAccountName(Long userId, String accountName);

    /**
     * 락을 걸고 계좌 조회 (사용자 ID와 계좌 ID로)
     */
    Optional<Account> findByIdAndUserIdWithLock(Long accountId, Long userId);

    /**
     * 계좌번호로 계좌 조회
     */
    Optional<Account> findByAccountNumber(String accountNumber);

    /**
     * 락을 걸고 계좌 조회 (계좌 ID만으로)
     */
    Optional<Account> findByIdWithLock(Long accountId);

    /**
     * 특정 거래 내역 조회 (권한 체크 포함)
     */
    Optional<AccountHistory> findHistoryByIdAndAccountId(Long historyId, Long accountId);

    /**
     * 통화별 계좌 거래 내역 조회 (페이지네이션)
     */
    Page<AccountHistory> findHistoryByAccountAndCurrency(
            Account account, String currency, Pageable pageable);

    /**
     * 기간별 계좌 거래 내역 조회 (페이지네이션)
     */
    Page<AccountHistory> findHistoryByAccountAndRange(
            Account account, LocalDateTime from, LocalDateTime to, Pageable pageable);

    /**
     * 환전 내역만 조회
     */
    List<AccountHistory> findExchangeHistoryByAccount(Account account);

    /**
     * 거래 유형별 총액 계산 (통계)
     */
    BigDecimal getTotalAmountByAccountAndType(Account account, TransactionType transactionType);
}
