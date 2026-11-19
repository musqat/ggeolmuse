package com.muscat.backtest.domain.repository;

public interface BacktestHistoryRepositoryCustom {

  // 이메일로 남은 옛 행의 주인을 토큰 사용자 식별자로 바꿈(옮긴 행 수, 영속성 컨텍스트는 비움)
  long reassignOwner(String from, String to);
}
