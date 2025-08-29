package com.muscat.trade.common.logging;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.muscat.trade.common.enums.type.TradeType;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.slf4j.Marker;
import org.slf4j.MarkerFactory;
import org.springframework.stereotype.Component;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.HashMap;
import java.util.Map;

@Component
@RequiredArgsConstructor
@Slf4j
public class TransactionLogger {

  private static final Marker TRADE_EVENT = MarkerFactory.getMarker("TRADE_EVENT");
  private final ObjectMapper objectMapper;

  public void logTrade(String tradeId, String userId, String accountId, String symbol,
                       TradeType tradeType, BigDecimal quantity, BigDecimal price, 
                       BigDecimal fee, BigDecimal totalAmount, LocalDate tradeDate) {
    
    Map<String, Object> tradeLog = new HashMap<>();
    tradeLog.put("eventType", "TRADE");
    tradeLog.put("tradeId", tradeId);
    tradeLog.put("userId", userId);
    tradeLog.put("accountId", accountId);
    tradeLog.put("symbol", symbol);
    tradeLog.put("tradeType", tradeType.name());
    tradeLog.put("quantity", quantity);
    tradeLog.put("price", price);
    tradeLog.put("fee", fee);
    tradeLog.put("totalAmount", totalAmount);
    tradeLog.put("tradeDate", tradeDate);
    tradeLog.put("executedAt", LocalDateTime.now());
    
    logStructured("TRADE_EXECUTED", tradeLog);
  }

  public void logDividend(String userId, String symbol, BigDecimal quantity, 
                         BigDecimal dividendPerShare, BigDecimal totalDividend, LocalDate dividendDate) {
    
    Map<String, Object> dividendLog = new HashMap<>();
    dividendLog.put("eventType", "DIVIDEND");
    dividendLog.put("userId", userId);
    dividendLog.put("symbol", symbol);
    dividendLog.put("quantity", quantity);
    dividendLog.put("dividendPerShare", dividendPerShare);
    dividendLog.put("totalDividend", totalDividend);
    dividendLog.put("dividendDate", dividendDate);
    dividendLog.put("processedAt", LocalDateTime.now());
    
    logStructured("DIVIDEND_PAID", dividendLog);
  }

  public void logHoldingsUpdate(String userId, String accountId, String symbol,
                               BigDecimal oldQuantity, BigDecimal newQuantity,
                               BigDecimal oldAvgPrice, BigDecimal newAvgPrice) {
    
    Map<String, Object> holdingsLog = new HashMap<>();
    holdingsLog.put("eventType", "HOLDINGS_UPDATE");
    holdingsLog.put("userId", userId);
    holdingsLog.put("accountId", accountId);
    holdingsLog.put("symbol", symbol);
    holdingsLog.put("oldQuantity", oldQuantity);
    holdingsLog.put("newQuantity", newQuantity);
    holdingsLog.put("oldAvgPrice", oldAvgPrice);
    holdingsLog.put("newAvgPrice", newAvgPrice);
    holdingsLog.put("updatedAt", LocalDateTime.now());
    
    logStructured("HOLDINGS_UPDATED", holdingsLog);
  }

  public void logFeeCalculation(String accountId, BigDecimal tradeAmount, 
                               BigDecimal commissionRate, BigDecimal fee) {
    
    Map<String, Object> feeLog = new HashMap<>();
    feeLog.put("eventType", "FEE_CALCULATION");
    feeLog.put("accountId", accountId);
    feeLog.put("tradeAmount", tradeAmount);
    feeLog.put("commissionRate", commissionRate);
    feeLog.put("calculatedFee", fee);
    feeLog.put("calculatedAt", LocalDateTime.now());
    
    logStructured("FEE_CALCULATED", feeLog);
  }

  public void logMarketDataRequest(String symbol, String dataType, boolean success, String error) {
    
    Map<String, Object> marketLog = new HashMap<>();
    marketLog.put("eventType", "MARKET_DATA_REQUEST");
    marketLog.put("symbol", symbol);
    marketLog.put("dataType", dataType);
    marketLog.put("success", success);
    if (error != null) {
      marketLog.put("error", error);
    }
    marketLog.put("requestedAt", LocalDateTime.now());
    
    logStructured("MARKET_DATA_REQUESTED", marketLog);
  }

  public void logBalanceCheck(String userId, String accountId, BigDecimal requiredAmount, 
                             BigDecimal availableAmount, boolean sufficient) {
    
    Map<String, Object> balanceLog = new HashMap<>();
    balanceLog.put("eventType", "BALANCE_CHECK");
    balanceLog.put("userId", userId);
    balanceLog.put("accountId", accountId);
    balanceLog.put("requiredAmount", requiredAmount);
    balanceLog.put("availableAmount", availableAmount);
    balanceLog.put("sufficient", sufficient);
    balanceLog.put("checkedAt", LocalDateTime.now());
    
    logStructured("BALANCE_CHECKED", balanceLog);
  }

  public void logPortfolioAccess(String userId, String accessType, String target, int recordCount) {
    
    Map<String, Object> portfolioLog = new HashMap<>();
    portfolioLog.put("eventType", "PORTFOLIO_ACCESS");
    portfolioLog.put("userId", userId);
    portfolioLog.put("accessType", accessType);
    if (target != null) {
      portfolioLog.put("target", target);
    }
    portfolioLog.put("recordCount", recordCount);
    portfolioLog.put("accessedAt", LocalDateTime.now());
    
    logStructured("PORTFOLIO_ACCESSED", portfolioLog);
  }

  private void logStructured(String action, Map<String, Object> data) {
    try {
      String jsonLog = objectMapper.writeValueAsString(data);
      log.info(TRADE_EVENT, "TRADE_EVENT action={} data={}", action, jsonLog);
    } catch (JsonProcessingException e) {
      log.error("구조화 로그 생성 실패: action={}", action, e);
      log.info(TRADE_EVENT, "TRADE_EVENT action={} data={}", action, data.toString());
    }
  }
}