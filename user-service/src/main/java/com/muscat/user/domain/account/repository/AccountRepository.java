package com.muscat.user.domain.account.repository;

import com.muscat.user.domain.account.entity.Account;
import java.util.List;
import org.springframework.data.jpa.repository.JpaRepository;

public interface AccountRepository extends JpaRepository<Account, Long>, AccountRepositoryCustom {

  // 사용자 ID로 모든 계좌 조회
  List<Account> findByUserId(Long userId);

  // 계좌번호 존재 여부 확인
  boolean existsByAccountNumber(String accountNumber);

}
