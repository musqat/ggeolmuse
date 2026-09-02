package com.muscat.backtest.domain.service;

import com.muscat.backtest.domain.dto.response.BacktestHistoryDto;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;

public interface BacktestHistoryService {

  //사용자별 백테스트 히스토리 조회 (페이징)
  Page<BacktestHistoryDto> getUserBacktestHistory(String userId, Pageable pageable);
}
