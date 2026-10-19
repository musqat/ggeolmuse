package com.muscat.user.domain.account.service.impl;

import com.muscat.messaging.event.TradeCompletedEvent;
import com.muscat.user.common.enums.responses.AccountResponse;
import com.muscat.user.common.enums.type.SettlementStatus;
import com.muscat.user.common.exceptions.AccountException;
import com.muscat.user.domain.account.entity.TradeSettlement;
import com.muscat.user.domain.account.repository.TradeSettlementRepository;
import com.muscat.user.domain.account.service.AccountService;
import com.muscat.user.domain.account.service.TradeSettlementService;
import com.muscat.user.infra.kafka.TradeRejectedEventProducer;
import com.muscat.user.infra.kafka.TradeSettledEventProducer;
import java.time.LocalDateTime;
import java.util.Arrays;
import java.util.Optional;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.transaction.PlatformTransactionManager;
import org.springframework.transaction.support.TransactionTemplate;

@Slf4j
@Service
public class TradeSettlementServiceImpl implements TradeSettlementService {

  private final AccountService accountService;
  private final TradeSettlementRepository tradeSettlementRepository;
  private final TradeRejectedEventProducer tradeRejectedEventProducer;
  private final TradeSettledEventProducer tradeSettledEventProducer;
  private final TransactionTemplate transactionTemplate;

  public TradeSettlementServiceImpl(AccountService accountService,
    TradeSettlementRepository tradeSettlementRepository,
    TradeRejectedEventProducer tradeRejectedEventProducer,
    TradeSettledEventProducer tradeSettledEventProducer,
    PlatformTransactionManager transactionManager) {
    this.accountService = accountService;
    this.tradeSettlementRepository = tradeSettlementRepository;
    this.tradeRejectedEventProducer = tradeRejectedEventProducer;
    this.tradeSettledEventProducer = tradeSettledEventProducer;
    this.transactionTemplate = new TransactionTemplate(transactionManager);
  }

  // 업무 실패(4xx)면 잔액은 그대로 두고 반영 실패를 남긴다. 그 밖의 예외는 그대로 던져 재시도한다
  @Override
  public void settle(TradeCompletedEvent event) {
    TradeSettlement outcome;
    try {
      outcome = transactionTemplate.execute(status -> apply(event));
    } catch (AccountException e) {
      if (!e.getHttpStatus().is4xxClientError()) {
        throw e;
      }
      outcome = transactionTemplate.execute(status -> reject(event, e));
    }
    announce(event, outcome);
  }

  // 처음 온 체결만 잔액 반영 · APPLIED 저장. 이미 처리한 체결은 잔액을 그대로 두고 기록된 결과를 돌려줌
  private TradeSettlement apply(TradeCompletedEvent event) {
    Optional<TradeSettlement> done = tradeSettlementRepository.findById(event.getTradeId());
    if (done.isPresent()) {
      log.info("이미 처리한 체결이라 결과만 다시 보냄: tradeId={}, status={}", event.getTradeId(), done.get().getStatus());
      return done.get();
    }

    accountService.processTradeEvent(event);
    TradeSettlement applied = TradeSettlement.applied(event.getTradeId(), event.getAccountId(), LocalDateTime.now());
    tradeSettlementRepository.saveAndFlush(applied);
    return applied;
  }

  // 커밋된 결과(정산됨 · 반영 실패) 알림. 실패하면 던져 다시 받고, 다시 받은 체결은 기록된 결과만 보냄
  private void announce(TradeCompletedEvent event, TradeSettlement outcome) {
    if (outcome.getStatus() == SettlementStatus.APPLIED) {
      tradeSettledEventProducer.publish(event);
    } else {
      tradeRejectedEventProducer.publish(event, outcome.getReasonCode(), reasonMessage(outcome.getReasonCode()));
    }
  }

  private static String reasonMessage(String reasonCode) {
    return Arrays.stream(AccountResponse.values())
      .filter(response -> response.name().equals(reasonCode))
      .findFirst()
      .map(AccountResponse::getMessage)
      .orElse(reasonCode);
  }

  // REJECTED 저장. 그 사이 다른 시도가 남긴 결과가 있으면 그것을 돌려줌
  private TradeSettlement reject(TradeCompletedEvent event, AccountException e) {
    Optional<TradeSettlement> done = tradeSettlementRepository.findById(event.getTradeId());
    if (done.isPresent()) {
      return done.get();
    }
    TradeSettlement rejected = TradeSettlement.rejected(
      event.getTradeId(), event.getAccountId(), e.getErrorCode(), LocalDateTime.now());
    tradeSettlementRepository.saveAndFlush(rejected);
    log.warn("체결을 잔액에 반영하지 못함: tradeId={}, reason={}", event.getTradeId(), e.getErrorCode());
    return rejected;
  }
}
