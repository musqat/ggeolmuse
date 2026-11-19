package com.muscat.trade.domain.repository;

import com.muscat.trade.domain.dto.TradePosition;
import java.util.List;

public interface DividendPayoutRepositoryCustom {

  // 배당 기록이 있는 사용자 · 계좌 · 종목 자리
  List<TradePosition> findDistinctPositions();
}
