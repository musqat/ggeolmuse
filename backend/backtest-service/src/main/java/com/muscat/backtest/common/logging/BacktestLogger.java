package com.muscat.backtest.common.logging;

import org.slf4j.MDC;

// 백테스트 서비스의 MDC 컨텍스트 관리 유틸리티
public final class BacktestLogger {

  private BacktestLogger() {
    throw new UnsupportedOperationException("이 클래스는 인스턴스를 생성할 수 없습니다");
  }

  public static final String USER_ID = "userId";
  public static final String OPERATION = "operation";

  // 사용자 ID를 MDC에 설정
  private static void setUserId(String userId) {
    if (userId != null && !userId.trim().isEmpty()) {
      MDC.put(USER_ID, userId);
    }
  }

  // 현재 수행 중인 작업을 MDC에 설정
  private static void setOperation(String operation) {
    if (operation != null && !operation.trim().isEmpty()) {
      MDC.put(OPERATION, operation);
    }
  }

  // 백테스트 컨텍스트 설정 (심볼 포함)
  public static void setBacktestContext(String userId, String operation, String symbol) {
    setUserId(userId);
    setOperation(operation);
    if (symbol != null) {
      MDC.put("symbol", symbol);
    }
  }

  // 전략 실행 컨텍스트 설정
  public static void setStrategyContext(String userId, String strategyType, String symbol) {
    setUserId(userId);
    setOperation("STRATEGY_" + strategyType);
    if (symbol != null) {
      MDC.put("symbol", symbol);
    }
  }

  // 특정 키만 제거
  public static void remove(String key) {
    MDC.remove(key);
  }

  // 모든 MDC 컨텍스트 정리
  public static void clear() {
    MDC.clear();
  }

}