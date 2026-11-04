package com.muscat.user.domain.account.repository;

import com.muscat.user.domain.account.entity.Account;
import org.springframework.data.jpa.repository.JpaRepository;

public interface AccountRepository extends JpaRepository<Account, Long>, AccountRepositoryCustom {

  // 계좌번호 존재 여부 확인
  boolean existsByAccountNumber(String accountNumber);

}
