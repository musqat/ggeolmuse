package com.muscat.user.domain.account.repository;

import com.muscat.user.domain.account.entity.Account;

import java.util.List;
import java.util.Optional;

public interface AccountRepositoryCustom {

    // JOIN FETCH로 User와 함께 Account 조회 - N+1 문제 방지
    List<Account> findByUserIdWithUser(Long userId);

    // 계좌 ID와 사용자 ID로 조회
    Optional<Account> findByIdAndUserId(Long accountId, Long userId);

    // 사용자 ID와 계좌명으로 중복 확인
    boolean existsByUserIdAndAccountName(Long userId, String accountName);

    // 락을 걸고 계좌 조회 (사용자 ID와 계좌 ID로)
    Optional<Account> findByIdAndUserIdWithLock(Long accountId, Long userId);

    // 계좌번호로 계좌 조회
    Optional<Account> findByAccountNumber(String accountNumber);

    // 락을 걸고 계좌 조회 (계좌 ID만으로)
    Optional<Account> findByIdWithLock(Long accountId);

}
