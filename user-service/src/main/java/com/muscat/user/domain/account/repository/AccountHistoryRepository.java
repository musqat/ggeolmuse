package com.muscat.user.domain.account.repository;

import com.muscat.user.common.enums.type.TransactionType;
import com.muscat.user.domain.account.entity.Account;
import com.muscat.user.domain.account.entity.AccountHistory;
import java.math.BigDecimal;
import java.time.Instant;
import java.time.LocalDateTime;
import java.util.List;
import java.util.Optional;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

public interface AccountHistoryRepository extends JpaRepository<AccountHistory, Long> {

  @Query("""
      select h
      from AccountHistory h
      where h.account = :account
        and h.currency = :currency
      order by h.createdAt desc, h.id desc
      """)
  Page<AccountHistory> findByAccountAndCurrencyOrderByCreatedAtDesc(
      @Param("account") Account account,
      @Param("currency") String currency,
      Pageable pageable
  );

  @Query("""
      select h
      from AccountHistory h
      where h.account = :account
        and (:from is null or h.createdAt >= :from)
        and (:to   is null  or h.createdAt <  :to)
      order by h.createdAt desc, h.id desc
      """)
  Page<AccountHistory> findByAccountAndRangeOrderByCreatedAtDesc(
      @Param("account") Account account,
      @Param("from") Instant from,
      @Param("to") Instant to,
      Pageable pageable
  );

  @Query("SELECT ah FROM AccountHistory ah WHERE ah.id = :historyId AND ah.account.id = :accountId")
  Optional<AccountHistory> findByIdAndAccountId(@Param("historyId") Long historyId,
      @Param("accountId") Long accountId);

  @Query("SELECT ah FROM AccountHistory ah WHERE ah.account = :account " +
      "AND ah.fromCurrency IS NOT NULL AND ah.toCurrency IS NOT NULL " +
      "ORDER BY ah.id DESC")
  List<AccountHistory> findExchangeHistoryByAccount(@Param("account") Account account);

  @Query("SELECT COALESCE(SUM(ah.amount), 0) FROM AccountHistory ah " +
      "WHERE ah.account = :account AND ah.transactionType = :transactionType")
  BigDecimal getTotalAmountByAccountAndType(
      @Param("account") Account account,
      @Param("transactionType") TransactionType transactionType);

  boolean existsByReferenceId(String referenceId);

  default Page<AccountHistory> findByAccountAndDateRangeOrderByCreatedAtDesc(
      Account account, LocalDateTime start, LocalDateTime end, Pageable pageable
  ) {
    Instant from =
        start == null ? null : start.atZone(java.time.ZoneId.systemDefault()).toInstant();
    Instant to = end == null ? null : end.atZone(java.time.ZoneId.systemDefault()).toInstant();
    return findByAccountAndRangeOrderByCreatedAtDesc(account, from, to, pageable);
  }

}