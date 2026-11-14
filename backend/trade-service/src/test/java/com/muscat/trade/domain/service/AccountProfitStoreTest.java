package com.muscat.trade.domain.service;

import static org.assertj.core.api.Assertions.assertThat;

import com.muscat.jpatest.TradeJpaTestConfig;
import com.muscat.trade.domain.entity.AccountProfitDaily;
import com.muscat.trade.domain.repository.AccountProfitDailyRepository;
import com.muscat.trade.domain.service.ProfitFlowCalculator.DailyProfit;
import java.math.BigDecimal;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.List;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.orm.jpa.DataJpaTest;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.context.ContextConfiguration;

@DataJpaTest(properties = "spring.flyway.enabled=false")
@ActiveProfiles("test")
@ContextConfiguration(classes = TradeJpaTestConfig.class)
@DisplayName("계좌 수익 흐름 저장")
class AccountProfitStoreTest {

  private static final LocalDate D1 = LocalDate.of(2024, 1, 2);

  @Autowired
  private AccountProfitDailyRepository accountProfitDailyRepository;

  private static AccountProfitDaily row(long accountId, LocalDate date, long profitKrw) {
    return AccountProfitDaily.of("user-1", accountId, new DailyProfit(date, new BigDecimal("1.00"),
      new BigDecimal("2.00"), new BigDecimal("3.00"), new BigDecimal("0.50"), new BigDecimal("1300.5"),
      BigDecimal.valueOf(profitKrw), new BigDecimal("10.00"), new BigDecimal("13005")),
      LocalDateTime.of(2024, 1, 5, 9, 0));
  }

  @Test
  @DisplayName("계좌를 지우면 그 계좌의 저장분만 사라진다")
  void deleteByAccountId_OnlyThatAccount() {
    accountProfitDailyRepository.saveAll(List.of(row(20L, D1, 100), row(21L, D1, 900)));

    accountProfitDailyRepository.deleteByAccountId(20L);

    assertThat(accountProfitDailyRepository.findAll()).extracting(AccountProfitDaily::getAccountId)
      .containsExactly(21L);
  }
}
