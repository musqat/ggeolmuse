package com.muscat.user.domain.account.repository;

import com.muscat.user.domain.account.entity.Account;
import jakarta.persistence.LockModeType;
import java.util.List;
import java.util.Optional;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Lock;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

// 계좌 Repository
public interface AccountRepository extends JpaRepository<Account, Long> {
  // 사용자별 계좌 조회
  List<Account> findByUserId(Long userId);

  // 사용자별 계좌 조회 (User 정보 함께 페치하여 N+1 최적화)
  @Query("SELECT a FROM Account a JOIN FETCH a.user WHERE a.user.id = :userId")
  List<Account> findByUserIdWithUser(@Param("userId") Long userId);

  // 계좌번호로 조회
  Optional<Account> findByAccountNumber(String accountNumber);

  // 사용자 + 계좌 ID로 조회 (권한 체크용)
  Optional<Account> findByIdAndUserId(Long accountId, Long userId);

  // 사용자별 계좌명 중복 체크
  boolean existsByUserIdAndAccountName(Long userId, String accountName);

  // 배타적 락으로 계좌 조회
  @Lock(LockModeType.PESSIMISTIC_WRITE)
  @Query("SELECT a FROM Account a WHERE a.id = :accountId AND a.user.id = :userId")
  Optional<Account> findByIdAndUserIdWithLock(@Param("accountId") Long accountId, @Param("userId") Long userId);

}
