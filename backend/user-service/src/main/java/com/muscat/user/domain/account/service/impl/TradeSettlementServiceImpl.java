package com.muscat.user.domain.account.service.impl;

import com.muscat.messaging.event.TradeCompletedEvent;
import com.muscat.user.domain.account.entity.TradeSettlement;
import com.muscat.user.domain.account.repository.TradeSettlementRepository;
import com.muscat.user.domain.account.service.AccountService;
import com.muscat.user.domain.account.service.TradeSettlementService;
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
  private final TransactionTemplate transactionTemplate;

  public TradeSettlementServiceImpl(AccountService accountService,
    TradeSettlementRepository tradeSettlementRepository,
    PlatformTransactionManager transactionManager) {
    this.accountService = accountService;
    this.tradeSettlementRepository = tradeSettlementRepository;
    this.transactionTemplate = new TransactionTemplate(transactionManager);
  }

  @Override
  public void settle(TradeCompletedEvent event) {
    transactionTemplate.executeWithoutResult(status -> apply(event));
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
}
