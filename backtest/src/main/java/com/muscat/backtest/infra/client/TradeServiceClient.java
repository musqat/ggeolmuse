package com.muscat.backtest.infra.client;

import com.muscat.backtest.common.response.ApiResponse;
import com.muscat.backtest.infra.client.dto.HoldingDto;
import com.muscat.backtest.infra.client.dto.TradeDto;
import com.muscat.backtest.infra.client.dto.TradeRequestDto;
import com.muscat.backtest.infra.client.dto.TradeResponseDto;
import java.util.List;
import org.springframework.cloud.openfeign.FeignClient;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestHeader;

// 트레이딩 서비스 클라이언트 - 포트폴리오, 보유 주식, 거래 내역 조회
@FeignClient(name = "trade-service", url = "${app.trade-service.url:http://localhost:8081}")
public interface TradeServiceClient {

  // 주식 매수 주문 실행
  @PostMapping("/api/trade/buy")
  ApiResponse<TradeResponseDto> buyStock(
      @RequestHeader("Authorization") String authorization,
      @RequestBody TradeRequestDto request
  );

  // 사용자 포트폴리오 조회
  @GetMapping("/api/portfolio")
  List<HoldingDto> getPortfolio(
      @RequestHeader("Authorization") String authorization
  );

  // 특정 사용자의 특정 심볼 보유 주식 조회
  @GetMapping("/api/holdings/user/{userId}/symbol/{symbol}")
  ApiResponse<List<HoldingDto>> getHoldingsByUserIdAndSymbol(
      @RequestHeader("Authorization") String authorization,
      @PathVariable("userId") String userId,
      @PathVariable("symbol") String symbol
  );

  // 보유 주식 ID로 상세 정보 조회
  @GetMapping("/api/holdings/{holdingId}")
  ApiResponse<HoldingDto> getHoldingById(
      @RequestHeader("Authorization") String authorization,
      @PathVariable("holdingId") String holdingId
  );

  // 특정 심볼의 거래 내역 조회 (백테스트에서 실제 매수 날짜 확인용)
  @GetMapping("/api/trade/history/{symbol}")
  List<TradeDto> getTradeHistoryBySymbol(
      @RequestHeader("Authorization") String authorization,
      @PathVariable("symbol") String symbol
  );
}