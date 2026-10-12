package com.muscat.user.infra.kafka;

import static com.muscat.user.infra.kafka.KafkaSends.failed;
import static com.muscat.user.infra.kafka.KafkaSends.sent;
import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatCode;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.BDDMockito.given;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;

import com.muscat.messaging.event.AccountDepositCompletedEvent;
import com.muscat.messaging.event.AccountWithdrawalCompletedEvent;
import com.muscat.user.domain.account.entity.Account;
import com.muscat.user.domain.user.entity.User;
import java.math.BigDecimal;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;
import org.springframework.kafka.core.KafkaTemplate;

@DisplayName("입출금 완료 발행")
class DepositWithdrawalEventProducerTest {

  private static final String DEPOSIT_TOPIC = "user.account.deposit.completed";
  private static final String WITHDRAWAL_TOPIC = "user.account.withdrawal.completed";

  @SuppressWarnings("unchecked")
  private final KafkaTemplate<String, AccountDepositCompletedEvent> depositTemplate = mock(KafkaTemplate.class);

  @SuppressWarnings("unchecked")
  private final KafkaTemplate<String, AccountWithdrawalCompletedEvent> withdrawalTemplate = mock(KafkaTemplate.class);

  private final DepositWithdrawalEventProducer producer =
    new DepositWithdrawalEventProducer(depositTemplate, withdrawalTemplate);

  // 반영이 끝난 계좌: 원화 150,000 · 달러 12.50
  private final Account account = Account.builder().id(3L).accountNumber("100-1")
    .user(User.builder().id(7L).build())
    .balanceKrw(new BigDecimal("150000")).balanceUsd(new BigDecimal("12.50")).build();

  private AccountDepositCompletedEvent sentDeposit() {
    ArgumentCaptor<AccountDepositCompletedEvent> sent = ArgumentCaptor.forClass(AccountDepositCompletedEvent.class);
    verify(depositTemplate).send(eq(DEPOSIT_TOPIC), eq("7"), sent.capture());
    return sent.getValue();
  }

  @Test
  @DisplayName("원화 입금은 사용자 id 를 키로, 입금 후 잔액을 원화 잔액으로 보낸다")
  void deposit_Krw() {
    given(depositTemplate.send(anyString(), anyString(), any())).willReturn(sent(DEPOSIT_TOPIC));

    producer.publishDepositCompleted(account, "KRW", new BigDecimal("50000"), new BigDecimal("100000"),
      "MANUAL", "ref-1", "입금");

    AccountDepositCompletedEvent event = sentDeposit();
    assertThat(event.getEventType()).isEqualTo("ACCOUNT_DEPOSIT_COMPLETED");
    assertThat(event.getUserId()).isEqualTo("7");
    assertThat(event.getAccountId()).isEqualTo(3L);
    assertThat(event.getDepositAmount()).isEqualByComparingTo("50000");
    assertThat(event.getBalanceBeforeDeposit()).isEqualByComparingTo("100000");
    assertThat(event.getBalanceAfterDeposit()).isEqualByComparingTo("150000");
    assertThat(event.getReferenceId()).isEqualTo("ref-1");
  }

  @Test
  @DisplayName("달러 입금의 입금 후 잔액은 달러 잔액이다")
  void deposit_Usd() {
    given(depositTemplate.send(anyString(), anyString(), any())).willReturn(sent(DEPOSIT_TOPIC));

    producer.publishDepositCompleted(account, "USD", new BigDecimal("2.50"), new BigDecimal("10.00"),
      "EXCHANGE", null, "환전 입금");

    assertThat(sentDeposit().getBalanceAfterDeposit()).isEqualByComparingTo("12.50");
  }

  @Test
  @DisplayName("출금은 출금 토픽으로 승인 여부와 통화별 출금 후 잔액을 보낸다")
  void withdrawal() {
    given(withdrawalTemplate.send(anyString(), anyString(), any())).willReturn(sent(WITHDRAWAL_TOPIC));

    producer.publishWithdrawalCompleted(account, "USD", new BigDecimal("5.00"), new BigDecimal("17.50"),
      "TRADE", "trade-9", "매수", true);
    producer.publishWithdrawalCompleted(account, "KRW", new BigDecimal("10000"), new BigDecimal("160000"),
      "EXCHANGE", null, "환전 출금", true);

    ArgumentCaptor<AccountWithdrawalCompletedEvent> sent = ArgumentCaptor.forClass(AccountWithdrawalCompletedEvent.class);
    verify(withdrawalTemplate, times(2)).send(eq(WITHDRAWAL_TOPIC), eq("7"), sent.capture());
    AccountWithdrawalCompletedEvent usd = sent.getAllValues().get(0);
    assertThat(usd.getEventType()).isEqualTo("ACCOUNT_WITHDRAWAL_COMPLETED");
    assertThat(usd.getWithdrawalAmount()).isEqualByComparingTo("5.00");
    assertThat(usd.getBalanceAfterWithdrawal()).isEqualByComparingTo("12.50");
    assertThat(usd.getApproved()).isTrue();
    assertThat(sent.getAllValues().get(1).getBalanceAfterWithdrawal()).isEqualByComparingTo("150000");
  }

  @Test
  @DisplayName("브로커가 받지 못해도 잔액 반영을 되돌리지 않게 호출한 쪽으로 예외를 올리지 않는다")
  void sendFails_DoesNotThrow() {
    given(depositTemplate.send(anyString(), anyString(), any())).willReturn(failed());
    given(withdrawalTemplate.send(anyString(), anyString(), any())).willReturn(failed());

    assertThatCode(() -> {
      producer.publishDepositCompleted(account, "KRW", BigDecimal.ONE, BigDecimal.ZERO, "MANUAL", null, null);
      producer.publishWithdrawalCompleted(account, "KRW", BigDecimal.ONE, BigDecimal.ONE, "MANUAL", null, null, true);
    }).doesNotThrowAnyException();
  }
}
