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
import static org.mockito.Mockito.verify;

import com.muscat.messaging.event.AccountBalanceUpdatedEvent;
import com.muscat.messaging.event.AccountDeletedEvent;
import com.muscat.user.domain.account.entity.Account;
import com.muscat.user.domain.user.entity.User;
import java.math.BigDecimal;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;
import org.springframework.kafka.core.KafkaTemplate;

@DisplayName("계좌 잔액 변경 · 삭제 발행")
class AccountEventProducerTest {

  private static final String BALANCE_TOPIC = "user.account.balance.updated";
  // trade-service 가 이 토픽을 읽어 그 계좌의 거래 · 보유를 지운다
  private static final String DELETED_TOPIC = "user.account.deleted";

  @SuppressWarnings("unchecked")
  private final KafkaTemplate<String, AccountBalanceUpdatedEvent> balanceTemplate = mock(KafkaTemplate.class);

  @SuppressWarnings("unchecked")
  private final KafkaTemplate<String, AccountDeletedEvent> deletedTemplate = mock(KafkaTemplate.class);

  private final AccountEventProducer producer = new AccountEventProducer(balanceTemplate, deletedTemplate);

  private final Account account = Account.builder().id(3L).accountNumber("100-1").accountName("연습")
    .user(User.builder().id(7L).build())
    .balanceKrw(new BigDecimal("150000")).balanceUsd(new BigDecimal("12.50")).build();

  @Test
  @DisplayName("잔액 변경은 이전 잔액 · 변화량은 받은 값으로, 현재 잔액은 계좌에서 읽어 보낸다")
  void balanceUpdated() {
    given(balanceTemplate.send(anyString(), anyString(), any())).willReturn(sent(BALANCE_TOPIC));

    producer.publishAccountBalanceUpdated(account, "EXCHANGE", new BigDecimal("163000"), new BigDecimal("2.50"),
      new BigDecimal("-13000"), new BigDecimal("10.00"), "환전", null, new BigDecimal("1300"));

    ArgumentCaptor<AccountBalanceUpdatedEvent> sent = ArgumentCaptor.forClass(AccountBalanceUpdatedEvent.class);
    verify(balanceTemplate).send(eq(BALANCE_TOPIC), eq("7"), sent.capture());
    AccountBalanceUpdatedEvent event = sent.getValue();
    assertThat(event.getEventType()).isEqualTo("ACCOUNT_BALANCE_UPDATED");
    assertThat(event.getUpdateType()).isEqualTo("EXCHANGE");
    assertThat(event.getPreviousBalanceKrw()).isEqualByComparingTo("163000");
    assertThat(event.getCurrentBalanceKrw()).isEqualByComparingTo("150000");
    assertThat(event.getPreviousBalanceUsd()).isEqualByComparingTo("2.50");
    assertThat(event.getCurrentBalanceUsd()).isEqualByComparingTo("12.50");
    assertThat(event.getKrwChange()).isEqualByComparingTo("-13000");
    assertThat(event.getExchangeRate()).isEqualByComparingTo("1300");
  }

  @Test
  @DisplayName("계좌 삭제는 trade 가 읽는 토픽으로 사용자 id 를 키로 보낸다")
  void deleted() {
    given(deletedTemplate.send(anyString(), anyString(), any())).willReturn(sent(DELETED_TOPIC));

    producer.publishAccountDeleted(account, "사용자 탈퇴");

    ArgumentCaptor<AccountDeletedEvent> sent = ArgumentCaptor.forClass(AccountDeletedEvent.class);
    verify(deletedTemplate).send(eq(DELETED_TOPIC), eq("7"), sent.capture());
    assertThat(sent.getValue().getEventType()).isEqualTo("ACCOUNT_DELETED");
    assertThat(sent.getValue().getAccountId()).isEqualTo(3L);
    assertThat(sent.getValue().getAccountName()).isEqualTo("연습");
    assertThat(sent.getValue().getDeletionReason()).isEqualTo("사용자 탈퇴");
  }

  @Test
  @DisplayName("브로커가 받지 못해도 호출한 쪽으로 예외를 올리지 않는다")
  void sendFails_DoesNotThrow() {
    given(balanceTemplate.send(anyString(), anyString(), any())).willReturn(failed());
    given(deletedTemplate.send(anyString(), anyString(), any())).willReturn(failed());

    assertThatCode(() -> {
      producer.publishAccountBalanceUpdated(account, "DEPOSIT", null, null, null, null, null, null, null);
      producer.publishAccountDeleted(account, "x");
    }).doesNotThrowAnyException();
  }
}
