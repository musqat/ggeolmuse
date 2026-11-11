package com.muscat.user.domain.account.repository;

import static org.assertj.core.api.Assertions.assertThat;

import com.muscat.jpatest.UserJpaTestConfig;
import com.muscat.user.common.enums.type.TransactionType;
import com.muscat.user.domain.account.entity.Account;
import com.muscat.user.domain.account.entity.AccountHistory;
import com.muscat.user.domain.user.entity.User;
import java.math.BigDecimal;
import java.time.LocalDateTime;
import java.util.List;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.orm.jpa.DataJpaTest;
import org.springframework.boot.test.autoconfigure.orm.jpa.TestEntityManager;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.context.ContextConfiguration;

@DataJpaTest(properties = "spring.flyway.enabled=false")
@ActiveProfiles("test")
@ContextConfiguration(classes = UserJpaTestConfig.class)
@DisplayName("계좌 장부 환전 줄 조회")
class AccountHistoryRepositoryTest {

  @Autowired
  private AccountHistoryRepository accountHistoryRepository;

  @Autowired
  private TestEntityManager entityManager;

  @Test
  @DisplayName("사용자의 모든 계좌 환전 줄을 생성 시각 · id 순으로 찾고 입금 줄과 다른 사용자의 계좌는 뺀다")
  void findExchanges_AllAccountsOfUser_OldestFirst() {
    User owner = persistUser("owner");
    Account first = persistAccount(owner, "100-1");
    Account second = persistAccount(owner, "100-2");
    Account othersAccount = persistAccount(persistUser("other"), "200-1");

    Long late = persistHistory(first, TransactionType.EXCHANGE, LocalDateTime.of(2024, 9, 5, 9, 0, 1));
    Long early = persistHistory(second, TransactionType.EXCHANGE, LocalDateTime.of(2024, 9, 2, 10, 15, 30));
    Long sameTime = persistHistory(first, TransactionType.EXCHANGE, LocalDateTime.of(2024, 9, 2, 10, 15, 30));
    persistHistory(first, TransactionType.DEPOSIT, LocalDateTime.of(2024, 9, 1, 9, 0, 1));
    persistHistory(othersAccount, TransactionType.EXCHANGE, LocalDateTime.of(2024, 9, 1, 9, 0, 1));
    entityManager.clear();

    List<AccountHistory> found = accountHistoryRepository
      .findByAccount_User_IdAndTransactionTypeOrderByCreatedAtAscIdAsc(owner.getId(), TransactionType.EXCHANGE);

    assertThat(found).extracting(AccountHistory::getId).containsExactly(early, sameTime, late);
    assertThat(found).extracting(history -> history.getAccount().getId())
      .containsExactly(second.getId(), first.getId(), first.getId());
  }

  @Test
  @DisplayName("환전 줄이 없는 사용자는 빈 목록")
  void findExchanges_NoExchange_Empty() {
    User owner = persistUser("owner");
    persistHistory(persistAccount(owner, "100-1"), TransactionType.DEPOSIT, LocalDateTime.of(2024, 9, 1, 9, 0, 1));
    entityManager.clear();

    assertThat(accountHistoryRepository
      .findByAccount_User_IdAndTransactionTypeOrderByCreatedAtAscIdAsc(owner.getId(), TransactionType.EXCHANGE))
      .isEmpty();
  }

  private User persistUser(String name) {
    return entityManager.persist(User.builder()
      .email(name + "@example.com")
      .nickname(name)
      .keycloakId("kc-" + name)
      .build());
  }

  private Account persistAccount(User user, String accountNumber) {
    return entityManager.persist(Account.builder()
      .user(user)
      .accountNumber(accountNumber)
      .accountName("연습")
      .build());
  }

  // 저장 때 채워지는 생성 시각을 SQL 로 바꿈
  private Long persistHistory(Account account, TransactionType type, LocalDateTime createdAt) {
    AccountHistory history = entityManager.persistAndFlush(AccountHistory.builder()
      .account(account)
      .transactionType(type)
      .amount(new BigDecimal("1000.00"))
      .currency("USD")
      .build());
    entityManager.getEntityManager()
      .createNativeQuery("update account_history set created_at = ?1 where id = ?2")
      .setParameter(1, createdAt)
      .setParameter(2, history.getId())
      .executeUpdate();
    return history.getId();
  }
}
