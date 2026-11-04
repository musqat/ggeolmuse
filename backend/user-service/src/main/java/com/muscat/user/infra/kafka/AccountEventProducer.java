package com.muscat.user.infra.kafka;

import com.muscat.messaging.event.AccountDeletedEvent;
import com.muscat.messaging.kafka.EventPublishing;
import com.muscat.user.domain.account.entity.Account;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.kafka.core.KafkaTemplate;
import org.springframework.stereotype.Component;

// 계좌 삭제 이벤트 발행
@Slf4j
@Component
@RequiredArgsConstructor
public class AccountEventProducer {

  private static final String ACCOUNT_DELETED_TOPIC = "user.account.deleted";

  private final KafkaTemplate<String, AccountDeletedEvent> accountDeletedKafkaTemplate;

  public void publishAccountDeleted(Account account, String deletionReason) {
    AccountDeletedEvent event = AccountDeletedEvent.builder()
      .userId(String.valueOf(account.getUser().getId()))
      .accountId(account.getId())
      .accountNumber(account.getAccountNumber())
      .accountName(account.getAccountName())
      .deletionReason(deletionReason)
      .build();
    EventPublishing.stamp(event, "ACCOUNT_DELETED", "user-service");

    log.info("계좌 삭제 이벤트 발행 중: accountId={}, accountNumber={}, userId={}",
      account.getId(), account.getAccountNumber(), account.getUser().getId());

    // 커밋 뒤 전송(롤백되면 trade 가 거래 · 보유를 지우지 않게), 이벤트는 엔티티가 살아 있는 지금 생성
    EventPublishing.sendAfterCommit(accountDeletedKafkaTemplate, ACCOUNT_DELETED_TOPIC, event.getUserId(), event);
  }
}
