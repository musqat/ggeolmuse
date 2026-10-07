package com.muscat.user.domain.account.service.impl;

import com.muscat.messaging.event.TradeCompletedEvent;
import com.muscat.user.common.exceptions.AccountException;
import com.muscat.user.domain.account.entity.TradeSettlement;
import com.muscat.user.domain.account.repository.TradeSettlementRepository;
import com.muscat.user.domain.account.service.AccountService;
import com.muscat.user.domain.account.service.TradeSettlementService;
import com.muscat.user.infra.kafka.TradeRejectedEventProducer;
import java.time.LocalDateTime;
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
  private final TransactionTemplate transactionTemplate;

  public TradeSettlementServiceImpl(AccountService accountService,
    TradeSettlementRepository tradeSettlementRepository,
    TradeRejectedEventProducer tradeRejectedEventProducer,
    PlatformTransactionManager transactionManager) {
    this.accountService = accountService;
    this.tradeSettlementRepository = tradeSettlementRepository;
    this.tradeRejectedEventProducer = tradeRejectedEventProducer;
    this.transactionTemplate = new TransactionTemplate(transactionManager);
  }

  // 업무 실패(4xx)면 잔액은 그대로 두고 반영 실패를 남긴다. 그 밖의 예외는 그대로 던져 재시도한다
  @Override
  public void settle(TradeCompletedEvent event) {
    try {
      transactionTemplate.executeWithoutResult(status -> apply(event));
    } catch (AccountException e) {
      if (!e.getHttpStatus().is4xxClientError()) {
        throw e;
      }
      transactionTemplate.executeWithoutResult(status -> reject(event, e));
    }
  }

  // 처음 온 체결만 잔액 반영 후 결과 저장
  private void apply(TradeCompletedEvent event) {
    if (tradeSettlementRepository.existsById(event.getTradeId())) {
      log.info("이미 처리한 체결이라 건너뜀: tradeId={}", event.getTradeId());
      return;
    }

    accountService.processTradeEvent(event);
    tradeSettlementRepository.saveAndFlush(
      TradeSettlement.applied(event.getTradeId(), event.getAccountId(), LocalDateTime.now()));
  }

  // REJECTED 저장 후 발행. 발행이 실패하면 저장도 롤백돼 재시도한다
  private void reject(TradeCompletedEvent event, AccountException e) {
    if (tradeSettlementRepository.existsById(event.getTradeId())) {
      return;
    }
    tradeSettlementRepository.saveAndFlush(TradeSettlement.rejected(
      event.getTradeId(), event.getAccountId(), e.getErrorCode(), LocalDateTime.now()));
    tradeRejectedEventProducer.publish(event, e.getErrorCode(), e.getErrorMessage());
    log.warn("체결을 잔액에 반영하지 못함: tradeId={}, reason={}", event.getTradeId(), e.getErrorCode());
  }
}
