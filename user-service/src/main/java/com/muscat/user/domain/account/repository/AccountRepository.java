package com.muscat.user.domain.account.repository;

import com.muscat.user.domain.account.entity.Account;
import java.util.List;
import java.util.Optional;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;

public interface AccountRepository extends JpaRepository<Account, Long> {

  List<Account> findByUserId(Long userId);

  Optional<Account> findByAccountNumber(String accountNumber);

  boolean existsByUserIdAndAccountName(Long userId, String accountName);

  @Query("SELECT a FROM Account a JOIN FETCH a.user WHERE a.user.id = :userId")
  List<Account> findByUserIdWithUser(Long userId);

}
