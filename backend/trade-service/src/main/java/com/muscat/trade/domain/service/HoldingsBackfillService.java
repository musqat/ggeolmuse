package com.muscat.trade.domain.service;

import com.muscat.trade.common.enums.type.TradeStatus;
import com.muscat.trade.domain.dto.TradePosition;
import com.muscat.trade.domain.repository.TradeRepository;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.transaction.support.TransactionTemplate;

// 완료 체결이 있는 사용자 · 계좌 · 종목마다 보유를 거래일 순서로 다시 쌓는 백필
@Slf4j
@Service
@RequiredArgsConstructor
public class HoldingsBackfillService {

  private final TradeRepository tradeRepository;
  private final HoldingsRebuilder holdingsRebuilder;
  private final TransactionTemplate transactionTemplate;

  // 자리마다 트랜잭션 하나로 다시 쌓고 바뀐 보유 행 수
  public int rebuildAll() {
    int changed = 0;
    int total = 0;
    for (TradePosition p : tradeRepository.findDistinctPositions(TradeStatus.COMPLETED)) {
      total++;
      if (Boolean.TRUE.equals(transactionTemplate.execute(
        status -> holdingsRebuilder.rebuild(p.userId(), p.accountId(), p.symbol(), false)))) {
        changed++;
      }
    }
    log.info("보유 다시 쌓기 백필: {} 자리 중 {} 행 바뀜", total, changed);
    return changed;
  }
}
