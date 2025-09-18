package com.muscat.backtest.infra.client;

import com.muscat.backtest.infra.client.dto.HoldingDto;
import com.muscat.backtest.infra.client.dto.TradeDto;
import java.util.List;
import org.springframework.cloud.openfeign.FeignClient;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestHeader;

// 트레이딩 서비스 클라이언트 - 포트폴리오, 보유 주식, 거래 내역 조회
@FeignClient(name = "trade-service", url = "${app.trade-service.url:http://trade-service:8081}")
public interface TradeServiceClient {


  // 사용자 포트폴리오 조회
  @GetMapping("/api/portfolio")
  List<HoldingDto> getPortfolio(
      @RequestHeader("Authorization") String authorization
  );

  // 특정 심볼의 거래 내역 조회 (백테스트에서 실제 매수 날짜 확인용)
  @GetMapping("/api/trade/internal/history/{symbol}")
  List<TradeDto> getTradeHistoryBySymbol(
      @RequestHeader("Authorization") String authorization,
      @PathVariable("symbol") String symbol
  );
}