package com.muscat.trade.common.logging;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;

import com.muscat.commonlib.logging.StructuredLogger;
import com.muscat.trade.common.enums.type.TradeType;
import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.Map;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;
import org.slf4j.Marker;

// 구조화 로거에 Map 으로 넘긴 거래 로그의 JSON 키 이름 고정(바뀌면 로그 검색이 깨짐)
@DisplayName("거래 구조화 로그")
class TradeLoggerTest {

  private final StructuredLogger structuredLogger = mock(StructuredLogger.class);
  private final TradeLogger tradeLogger = new TradeLogger(structuredLogger);

  @SuppressWarnings("unchecked")
  private Map<String, Object> logged(String action) {
    ArgumentCaptor<Map<String, Object>> data = ArgumentCaptor.forClass(Map.class);
    verify(structuredLogger).logStructured(any(Marker.class), eq(action), data.capture());
    return data.getValue();
  }

  @Test
  @DisplayName("체결 로그는 거래 번호 · 사용자 · 계좌 · 종목 · 매매 구분 · 수량 · 가격 · 수수료 · 금액 · 거래일을 담는다")
  void logTrade() {
    tradeLogger.logTrade(7L, "user-1", "20", "AAPL", TradeType.BUY, new BigDecimal("2"), new BigDecimal("150"),
      new BigDecimal("0.30"), new BigDecimal("300.30"), LocalDate.of(2026, 10, 7));

    Map<String, Object> data = logged("TRADE_EXECUTED");
    assertThat(data).containsEntry("eventType", "TRADE").containsEntry("tradeId", 7L).containsEntry("userId", "user-1")
      .containsEntry("accountId", "20").containsEntry("symbol", "AAPL").containsEntry("tradeType", "BUY")
      .containsEntry("tradeDate", LocalDate.of(2026, 10, 7)).containsKey("executedAt");
    assertThat((BigDecimal) data.get("totalAmount")).isEqualByComparingTo("300.30");
  }

  @Test
  @DisplayName("보유 변경 · 수수료 계산 로그는 전후 값을 담는다")
  void holdingsAndFee() {
    tradeLogger.logHoldingsUpdate("user-1", "20", "AAPL", BigDecimal.TEN, new BigDecimal("12"),
      new BigDecimal("150"), new BigDecimal("152"));
    tradeLogger.logFeeCalculation("20", new BigDecimal("300"), new BigDecimal("0.001"), new BigDecimal("0.30"));

    Map<String, Object> holdings = logged("HOLDINGS_UPDATED");
    Map<String, Object> fee = logged("FEE_CALCULATED");
    assertThat(holdings).containsEntry("eventType", "HOLDINGS_UPDATE").containsEntry("oldQuantity", BigDecimal.TEN)
      .containsEntry("newQuantity", new BigDecimal("12")).containsKey("updatedAt");
    assertThat(fee).containsEntry("eventType", "FEE_CALCULATION").containsEntry("calculatedFee", new BigDecimal("0.30"))
      .containsEntry("commissionRate", new BigDecimal("0.001"));
  }

  @Test
  @DisplayName("시세 요청 로그는 실패했을 때만 오류를 담는다")
  void marketDataRequest_ErrorOnlyOnFailure() {
    tradeLogger.logMarketDataRequest("AAPL", "PRICE", true, null);
    assertThat(logged("MARKET_DATA_REQUESTED")).containsEntry("success", true).doesNotContainKey("error");

    StructuredLogger other = mock(StructuredLogger.class);
    new TradeLogger(other).logMarketDataRequest("AAPL", "PRICE", false, "timeout");
    assertThat(loggedOn(other, "MARKET_DATA_REQUESTED")).containsEntry("success", false).containsEntry("error", "timeout");
  }

  @Test
  @DisplayName("포트폴리오 접근 로그는 대상이 있을 때만 대상을 담는다")
  void portfolioAccess_TargetOnlyWhenGiven() {
    tradeLogger.logPortfolioAccess("user-1", "LIST", null, 3);
    assertThat(logged("PORTFOLIO_ACCESSED")).containsEntry("recordCount", 3).doesNotContainKey("target");

    StructuredLogger other = mock(StructuredLogger.class);
    new TradeLogger(other).logPortfolioAccess("user-1", "DETAIL", "AAPL", 1);
    assertThat(loggedOn(other, "PORTFOLIO_ACCESSED")).containsEntry("target", "AAPL");
  }

  @SuppressWarnings("unchecked")
  private static Map<String, Object> loggedOn(StructuredLogger logger, String action) {
    ArgumentCaptor<Map<String, Object>> data = ArgumentCaptor.forClass(Map.class);
    verify(logger).logStructured(any(Marker.class), eq(action), data.capture());
    return data.getValue();
  }

  @Test
  @DisplayName("잔액 검사 로그는 필요 금액 · 보유 금액 · 충분 여부를 담는다")
  void balanceCheck() {
    tradeLogger.logBalanceCheck("user-1", "20", new BigDecimal("300.30"), new BigDecimal("100"), false);

    Map<String, Object> data = logged("BALANCE_CHECKED");
    assertThat(data).containsEntry("eventType", "BALANCE_CHECK").containsEntry("sufficient", false)
      .containsEntry("requiredAmount", new BigDecimal("300.30")).containsEntry("availableAmount", new BigDecimal("100"));
  }
}
