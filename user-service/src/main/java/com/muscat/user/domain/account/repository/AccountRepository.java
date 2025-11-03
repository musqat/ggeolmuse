package com.muscat.user.domain.account.repository;

import com.muscat.user.domain.account.entity.Account;
import java.util.List;
import org.springframework.data.jpa.repository.JpaRepository;

/**
 * Account Repository
 */
public interface AccountRepository extends JpaRepository<Account, Long>, AccountRepositoryCustom {

  /**
   * 사용자 ID로 모든 계좌 조회 (단순 조회)
   */
  List<Account> findByUserId(Long userId);

}
