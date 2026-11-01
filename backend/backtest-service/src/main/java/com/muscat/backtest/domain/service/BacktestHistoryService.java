package com.muscat.backtest.domain.service;

import com.muscat.backtest.domain.dto.response.BacktestHistoryDto;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;

public interface BacktestHistoryService {

  // 사용자별 이력 조회(페이징, 이메일로 남은 옛 행이 있으면 먼저 사용자 식별자로 옮김)
  Page<BacktestHistoryDto> getUserBacktestHistory(String owner, String legacyOwner, Pageable pageable);
}
