package com.muscat.backtest.common.logging;

import org.slf4j.MDC;
import org.springframework.stereotype.Component;

// 백테스트 서비스의 MDC 컨텍스트 관리 유틸리티
@Component
public class BacktestLogger {

  public static final String USER_ID = "userId";
  public static final String OPERATION = "operation";

  // 사용자 ID를 MDC에 설정
  public static void setUserId(String userId) {
    if (userId != null && !userId.trim().isEmpty()) {
      MDC.put(USER_ID, userId);
    }
  }

  // 현재 수행 중인 작업을 MDC에 설정
  public static void setOperation(String operation) {
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

  // 분석 컨텍스트 설정
  public static void setAnalysisContext(String userId, String analysisType) {
    setUserId(userId);
    setOperation("ANALYSIS_" + analysisType);
  }

  // 특정 키만 제거
  public static void remove(String key) {
    MDC.remove(key);
  }
  
  // 로깅 패턴 통일을 위한 유틸리티 메서드들
  public static void logRequest(String operation, String symbol, String details) {
    log.info("{} 요청: {} - {}", operation, symbol, details);
  }
  
  public static void logCompletion(String operation, String symbol, String result) {
    log.info("{} 완료: {} - {}", operation, symbol, result);
  }
  
  // slf4j Logger를 위한 정적 로거
  private static final org.slf4j.Logger log = org.slf4j.LoggerFactory.getLogger(BacktestLogger.class);

}