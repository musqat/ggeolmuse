package com.muscat.trade.domain.controller;

import com.muscat.trade.domain.dto.request.TradeRequestDto;
import com.muscat.trade.domain.dto.request.TradingCapacityRequestDto;
import com.muscat.trade.domain.dto.response.TradeResponseDto;
import com.muscat.trade.domain.dto.response.TradingCapacityResponseDto;
import com.muscat.trade.domain.service.TradingService;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.Parameter;
import io.swagger.v3.oas.annotations.media.Content;
import io.swagger.v3.oas.annotations.media.ExampleObject;
import io.swagger.v3.oas.annotations.media.Schema;
import io.swagger.v3.oas.annotations.responses.ApiResponse;
import io.swagger.v3.oas.annotations.responses.ApiResponses;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.http.ProblemDetail;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.security.oauth2.jwt.Jwt;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api/trade")
@RequiredArgsConstructor
@Slf4j
@Tag(name = "거래 관리", description = "주식 매수/매도 및 거래 가능 수량 계산 API")
public class TradingController {

  private final TradingService tradingService;

  @Operation(
      summary = "주식 매수",
      description = "지정된 계좌에서 주식을 매수합니다. 시장가 또는 지정가 주문이 가능합니다."
  )
  @ApiResponses(value = {
      @ApiResponse(
          responseCode = "200",
          description = "매수 성공",
          content = @Content(
              mediaType = "application/json",
              schema = @Schema(implementation = TradeResponseDto.class),
              examples = @ExampleObject(
                  value = """
                  {
                    "tradeId": 12345,
                    "symbol": "AAPL",
                    "tradeType": "BUY",
                    "quantity": 10,
                    "price": 238.15,
                    "totalAmount": 2381.50,
                    "tradeDate": "2024-09-18",
                    "accountId": 1
                  }
                  """
              )
          )
      ),
      @ApiResponse(
          responseCode = "400",
          description = "잘못된 요청 또는 잔액 부족",
          content = @Content(
              mediaType = "application/json",
              schema = @Schema(implementation = ProblemDetail.class)
          )
      ),
      @ApiResponse(
          responseCode = "404",
          description = "계좌 또는 종목을 찾을 수 없음",
          content = @Content(
              mediaType = "application/json",
              schema = @Schema(implementation = ProblemDetail.class)
          )
      )
  })
  @PostMapping("/buy")
  public ResponseEntity<TradeResponseDto> buyStock(
      @Parameter(description = "JWT 토큰에서 추출되는 사용자 ID", hidden = true)
      @AuthenticationPrincipal Jwt jwt,
      @Parameter(description = "매수 요청 정보", required = true)
      @Valid @RequestBody TradeRequestDto request) {

    String userId = jwt.getSubject();

    log.info("매수 요청: userId={}, accountId={}, symbol={}, quantity={}, tradeDate={}",
        userId, request.getAccountId(), request.getSymbol(), request.getQuantity(),
        request.getTradeDate());

    TradeResponseDto trade = tradingService.buyStock(
        userId,
        Long.valueOf(request.getAccountId()),
        request.getSymbol(),
        request.getQuantity(),
        request.getTradeDate(),
        request.getPriceType(),
        request.getManualPrice()
    );

    return ResponseEntity.ok(trade);
  }

  @Operation(
      summary = "주식 매도",
      description = "지정된 계좌에서 보유한 주식을 매도합니다. 시장가 또는 지정가 주문이 가능합니다."
  )
  @ApiResponses(value = {
      @ApiResponse(
          responseCode = "200",
          description = "매도 성공",
          content = @Content(
              mediaType = "application/json",
              schema = @Schema(implementation = TradeResponseDto.class),
              examples = @ExampleObject(
                  value = """
                  {
                    "tradeId": 12346,
                    "symbol": "AAPL",
                    "tradeType": "SELL",
                    "quantity": 5,
                    "price": 238.15,
                    "totalAmount": 1190.75,
                    "tradeDate": "2024-09-18",
                    "accountId": 1
                  }
                  """
              )
          )
      ),
      @ApiResponse(
          responseCode = "400",
          description = "잘못된 요청 또는 보유 수량 부족",
          content = @Content(
              mediaType = "application/json",
              schema = @Schema(implementation = ProblemDetail.class)
          )
      ),
      @ApiResponse(
          responseCode = "404",
          description = "계좌 또는 종목을 찾을 수 없음",
          content = @Content(
              mediaType = "application/json",
              schema = @Schema(implementation = ProblemDetail.class)
          )
      )
  })
  @PostMapping("/sell")
  public ResponseEntity<TradeResponseDto> sellStock(
      @Parameter(description = "JWT 토큰에서 추출되는 사용자 ID", hidden = true)
      @AuthenticationPrincipal Jwt jwt,
      @Parameter(description = "매도 요청 정보", required = true)
      @Valid @RequestBody TradeRequestDto request) {

    String userId = jwt.getSubject();

    log.info("매도 요청: userId={}, accountId={}, symbol={}, quantity={}, tradeDate={}",
        userId, request.getAccountId(), request.getSymbol(), request.getQuantity(),
        request.getTradeDate());

    TradeResponseDto trade = tradingService.sellStock(
        userId,
        Long.valueOf(request.getAccountId()),
        request.getSymbol(),
        request.getQuantity(),
        request.getTradeDate(),
        request.getPriceType(),
        request.getManualPrice()
    );

    return ResponseEntity.ok(trade);
  }


  @Operation(
      summary = "매수 가능 수량 계산",
      description = "현재 계좌 잔액으로 매수 가능한 주식 수량을 계산합니다"
  )
  @ApiResponses(value = {
      @ApiResponse(
          responseCode = "200",
          description = "매수 가능 수량 계산 성공",
          content = @Content(
              mediaType = "application/json",
              schema = @Schema(implementation = TradingCapacityResponseDto.class),
              examples = @ExampleObject(
                  value = """
                  {
                    "symbol": "AAPL",
                    "currentPrice": 238.15,
                    "availableQuantity": 42,
                    "totalAmount": 10002.30,
                    "accountBalance": 10000.00,
                    "tradeDate": "2024-09-18"
                  }
                  """
              )
          )
      ),
      @ApiResponse(
          responseCode = "400",
          description = "잘못된 요청",
          content = @Content(
              mediaType = "application/json",
              schema = @Schema(implementation = ProblemDetail.class)
          )
      ),
      @ApiResponse(
          responseCode = "404",
          description = "계좌 또는 종목을 찾을 수 없음",
          content = @Content(
              mediaType = "application/json",
              schema = @Schema(implementation = ProblemDetail.class)
          )
      )
  })
  @PostMapping("/can-buy")
  public ResponseEntity<TradingCapacityResponseDto> calculateBuyingCapacity(
      @Parameter(description = "JWT 토큰에서 추출되는 사용자 ID", hidden = true)
      @AuthenticationPrincipal Jwt jwt,
      @Parameter(description = "매수 가능 수량 계산 요청 정보", required = true)
      @Valid @RequestBody TradingCapacityRequestDto request) {

    String userId = jwt.getSubject();

    log.info("매수 가능 수량 계산 요청: userId={}, accountId={}, symbol={}, tradeDate={}",
        userId, request.getAccountId(), request.getSymbol(), request.getTradeDate());

    TradingCapacityResponseDto result = tradingService.calculateBuyingCapacity(userId, request);

    return ResponseEntity.ok(result);
  }

  @Operation(
      summary = "매도 가능 수량 계산",
      description = "현재 보유한 주식 중 매도 가능한 수량을 계산합니다"
  )
  @ApiResponses(value = {
      @ApiResponse(
          responseCode = "200",
          description = "매도 가능 수량 계산 성공",
          content = @Content(
              mediaType = "application/json",
              schema = @Schema(implementation = TradingCapacityResponseDto.class),
              examples = @ExampleObject(
                  value = """
                  {
                    "symbol": "AAPL",
                    "currentPrice": 238.15,
                    "availableQuantity": 25,
                    "totalAmount": 5953.75,
                    "holdingQuantity": 25,
                    "tradeDate": "2024-09-18"
                  }
                  """
              )
          )
      ),
      @ApiResponse(
          responseCode = "400",
          description = "잘못된 요청",
          content = @Content(
              mediaType = "application/json",
              schema = @Schema(implementation = ProblemDetail.class)
          )
      ),
      @ApiResponse(
          responseCode = "404",
          description = "계좌 또는 종목을 찾을 수 없음",
          content = @Content(
              mediaType = "application/json",
              schema = @Schema(implementation = ProblemDetail.class)
          )
      )
  })
  @PostMapping("/can-sell")
  public ResponseEntity<TradingCapacityResponseDto> calculateSellingCapacity(
      @Parameter(description = "JWT 토큰에서 추출되는 사용자 ID", hidden = true)
      @AuthenticationPrincipal Jwt jwt,
      @Parameter(description = "매도 가능 수량 계산 요청 정보", required = true)
      @Valid @RequestBody TradingCapacityRequestDto request) {

    String userId = jwt.getSubject();

    log.info("매도 가능 수량 계산 요청: userId={}, accountId={}, symbol={}, tradeDate={}",
        userId, request.getAccountId(), request.getSymbol(), request.getTradeDate());

    TradingCapacityResponseDto result = tradingService.calculateSellingCapacity(userId, request);

    return ResponseEntity.ok(result);
  }

}