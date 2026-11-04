package com.muscat.trade.domain.dto.response;

import com.muscat.trade.domain.entity.Trade;
import java.math.BigDecimal;
import java.time.LocalDate;
import java.time.LocalDateTime;

// 통합 거래 내역 한 줄(매매와 배당은 칸이 달라 레코드 둘)
public sealed interface TransactionHistoryItem {

  // 거래일(배당은 배당락일), 최신순 정렬 기준
  LocalDate date();

  record TradeItem(
    String type,
    Long tradeId,
    Long accountId,
    String symbol,
    BigDecimal quantity,
    BigDecimal price,
    BigDecimal totalAmount,
    BigDecimal fee,
    LocalDate date,
    LocalDateTime executedAt,
    String status,
    String cancelReason
  ) implements TransactionHistoryItem {

    public static TradeItem from(Trade trade) {
      return new TradeItem(trade.getTradeType().name(), trade.getId(), trade.getAccountId(),
        trade.getSymbol(), trade.getQuantity(), trade.getPrice(), trade.getTotalAmount(), trade.getFee(),
        trade.getTradeDate(), trade.getExecutedAt(), trade.getStatus().name(), trade.getCancelReason());
    }
  }

  // 수량 · 단가 · 수수료는 늘 빈 값, 금액은 세후
  record DividendItem(
    String type,
    Long accountId,
    String symbol,
    BigDecimal quantity,
    BigDecimal price,
    BigDecimal totalAmount,
    BigDecimal grossAmount,
    BigDecimal taxAmount,
    BigDecimal fee,
    LocalDate date,
    LocalDateTime executedAt,
    BigDecimal dividendPerShare,
    BigDecimal shares
  ) implements TransactionHistoryItem {

    public static DividendItem from(DividendResponseDto dividend) {
      return new DividendItem("DIVIDEND", dividend.accountId(), dividend.symbol(), null, null,
        dividend.netAmount(), dividend.grossAmount(), dividend.taxAmount(), null, dividend.dividendDate(),
        dividend.processedAt(), dividend.dividendPerShare(), dividend.shares());
    }
  }
}
