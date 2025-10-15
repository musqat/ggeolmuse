package com.muscat.user.domain.account.repository;

import com.muscat.user.domain.account.entity.Account;
import com.muscat.user.domain.account.entity.AccountHistory;
import org.springframework.data.jpa.repository.JpaRepository;

public interface AccountHistoryRepository extends JpaRepository<AccountHistory, Long> {

  //참조 ID로 거래 내역 존재 여부 확인 (중복 거래 방지용)
  boolean existsByReferenceId(String referenceId);

  //계좌 삭제 시 관련된 모든 거래 내역 삭제
  void deleteByAccount(Account account);

}
