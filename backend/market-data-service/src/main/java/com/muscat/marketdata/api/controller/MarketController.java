package com.muscat.marketdata.api.controller;

import com.muscat.marketdata.domain.dto.DividendDto;
import com.muscat.marketdata.domain.dto.OHLCPriceDto;
import com.muscat.marketdata.domain.dto.StockPriceDto;
import com.muscat.marketdata.domain.dto.SymbolDto;
import com.muscat.marketdata.domain.entity.FxRate;
import com.muscat.marketdata.domain.service.AssetService;
import com.muscat.marketdata.domain.service.CandleService;
import com.muscat.marketdata.domain.service.DividendService;
import com.muscat.marketdata.domain.service.FxRateService;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.Parameter;
import io.swagger.v3.oas.annotations.media.Content;
import io.swagger.v3.oas.annotations.media.ExampleObject;
import io.swagger.v3.oas.annotations.media.Schema;
import io.swagger.v3.oas.annotations.responses.ApiResponse;
import io.swagger.v3.oas.annotations.responses.ApiResponses;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Pattern;
import jakarta.validation.constraints.Positive;
import jakarta.validation.constraints.Size;
import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.List;
import java.util.Map;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.format.annotation.DateTimeFormat;
import org.springframework.http.HttpStatus;
import org.springframework.http.ProblemDetail;
import org.springframework.http.ResponseEntity;
import org.springframework.validation.annotation.Validated;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

@Slf4j
@RestController
@Validated
@RequestMapping("/api/market")
@RequiredArgsConstructor
@Tag(name = "시장 데이터", description = "공개 시장 데이터 API - 주식 시세, OHLC 데이터, 배당 정보, 환율 조회 (인증 선택적)")
public class MarketController {

  private final CandleService candleService;
  private final DividendService dividendService;
  private final AssetService assetService;
  private final FxRateService fxRateService;

  @Operation(
    summary = "OHLC 데이터 조회",
    description = "지정된 종목과 날짜의 OHLC(시가, 고가, 저가, 종가) 데이터를 조회합니다"
  )
  @ApiResponses(value = {
    @ApiResponse(
      responseCode = "200",
      description = "OHLC 데이터 조회 성공",
      content = @Content(
        mediaType = "application/json",
        schema = @Schema(implementation = OHLCPriceDto.class)
      )
    ),
    @ApiResponse(
      responseCode = "404",
      description = "해당 날짜의 데이터를 찾을 수 없음",
      content = @Content(
        mediaType = "application/json",
        schema = @Schema(implementation = ProblemDetail.class)
      )
    )
  })
  @GetMapping("/ohlc/{symbol}")
  public ResponseEntity<OHLCPriceDto> getOHLCPrice(
    @Parameter(description = "종목 코드 (예: AAPL, MSFT)", example = "AAPL", required = true)
    @PathVariable @NotBlank @Pattern(regexp = "^[A-Z]{1,16}$") String symbol,
    @Parameter(description = "조회할 날짜", example = "2024-01-15", required = true)
    @RequestParam @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate date) {

    log.debug("OHLC 조회 요청: symbol={}, date={}", symbol, date);

    OHLCPriceDto result = candleService.getOHLCPrice(symbol, date);

    return ResponseEntity.status(HttpStatus.OK).body(result);
  }

  @Operation(
    summary = "주식 현재가 조회",
    description = "지정된 종목의 현재가 정보를 조회합니다"
  )
  @ApiResponses(value = {
    @ApiResponse(
      responseCode = "200",
      description = "현재가 조회 성공",
      content = @Content(
        mediaType = "application/json",
        schema = @Schema(implementation = StockPriceDto.class),
        examples = @ExampleObject(
          value = """
            {
              "symbol": "AAPL",
              "price": 238.15,
              "timestamp": "2024-09-18T15:30:00Z"
            }
            """
        )
      )
    ),
    @ApiResponse(
      responseCode = "404",
      description = "종목을 찾을 수 없음",
      content = @Content(
        mediaType = "application/json",
        schema = @Schema(implementation = ProblemDetail.class)
      )
    )
  })
  @GetMapping("/price/{symbol}")
  public ResponseEntity<StockPriceDto> getCurrentPrice(
    @Parameter(description = "종목 코드 (예: AAPL, MSFT)", example = "AAPL", required = true)
    @PathVariable @NotBlank @Pattern(regexp = "^[A-Z]{1,16}$") String symbol) {
    log.debug("현재가 조회 요청: symbol={}", symbol);

    StockPriceDto result = candleService.getCurrentPrice(symbol);

    return ResponseEntity.status(HttpStatus.OK).body(result);
  }

  @Operation(
    summary = "여러 종목 현재가 일괄 조회",
    description = "여러 종목의 현재가를 한 번에 조회합니다"
  )
  @ApiResponses(value = {
    @ApiResponse(
      responseCode = "200",
      description = "현재가 일괄 조회 성공",
      content = @Content(
        mediaType = "application/json"
      )
    )
  })
  @GetMapping("/prices")
  public ResponseEntity<Map<String, StockPriceDto>> getCurrentPrices(
    @Parameter(description = "종목 코드 리스트 (예: AAPL,MSFT,GOOGL)", required = true)
    @RequestParam("symbols") List<String> symbols) {
    log.debug("다중 현재가 조회 요청: symbols={}", symbols);

    Map<String, StockPriceDto> results = new java.util.HashMap<>();
    for (String symbol : symbols) {
      try {
        StockPriceDto price = candleService.getCurrentPrice(symbol.toUpperCase());
        results.put(symbol.toUpperCase(), price);
      } catch (Exception e) {
        log.warn("현재가 조회 실패: symbol={}, error={}", symbol, e.getMessage());
      }
    }

    return ResponseEntity.status(HttpStatus.OK).body(results);
  }

  @Operation(
    summary = "날짜별 환율 조회",
    description = "지정된 날짜의 USD/KRW 환율을 조회합니다"
  )
  @ApiResponses(value = {
    @ApiResponse(
      responseCode = "200",
      description = "환율 조회 성공",
      content = @Content(
        mediaType = "application/json",
        schema = @Schema(implementation = FxRate.class)
      )
    ),
    @ApiResponse(
      responseCode = "404",
      description = "해당 날짜의 환율 데이터가 없음"
    )
  })
  @GetMapping("/fx/{date}")
  public ResponseEntity<FxRate> getFxRate(
    @Parameter(description = "조회할 날짜 (YYYY-MM-DD)", example = "2024-01-15", required = true)
    @PathVariable LocalDate date) {
    log.debug("환율 조회 요청: date={}", date);

    FxRate fxRate = fxRateService.findByDate(date);

    if (fxRate == null) {
      return ResponseEntity.status(HttpStatus.NOT_FOUND).build();
    }

    return ResponseEntity.status(HttpStatus.OK).body(fxRate);
  }

  @Operation(
    summary = "최신 환율 조회",
    description = "가장 최근의 USD/KRW 환율을 조회합니다"
  )
  @ApiResponses(value = {
    @ApiResponse(
      responseCode = "200",
      description = "최신 환율 조회 성공",
      content = @Content(
        mediaType = "application/json",
        schema = @Schema(implementation = FxRate.class)
      )
    ),
    @ApiResponse(
      responseCode = "404",
      description = "환율 데이터가 없음"
    )
  })
  @GetMapping("/fx/latest")
  public ResponseEntity<FxRate> getLatestFxRate() {
    log.debug("최신 환율 조회 요청");

    var fxRate = fxRateService.getLatestRate();

    if (fxRate.isEmpty()) {
      return ResponseEntity.status(HttpStatus.NOT_FOUND).build();
    }

    return ResponseEntity.status(HttpStatus.OK).body(fxRate.get());
  }

  @Operation(
    summary = "배당 이력 조회",
    description = "지정된 종목의 배당 지급 이력을 조회합니다"
  )
  @ApiResponses(value = {
    @ApiResponse(
      responseCode = "200",
      description = "배당 이력 조회 성공",
      content = @Content(
        mediaType = "application/json",
        schema = @Schema(implementation = DividendDto.class)
      )
    ),
    @ApiResponse(
      responseCode = "404",
      description = "종목을 찾을 수 없거나 배당 데이터가 없음"
    )
  })
  @GetMapping("/dividend/{symbol}")
  public ResponseEntity<List<DividendDto>> getDividendHistory(
    @Parameter(description = "종목 코드 (예: AAPL, MSFT)", example = "AAPL", required = true)
    @PathVariable @NotBlank @Pattern(regexp = "^[A-Z]{1,16}$") String symbol,
    @Parameter(description = "시작 날짜 (선택사항)", example = "2024-01-01")
    @RequestParam(required = false) LocalDate startDate,
    @Parameter(description = "종료 날짜 (선택사항)", example = "2024-12-31")
    @RequestParam(required = false) LocalDate endDate) {
    log.debug("배당 이력 조회 요청: symbol={}, startDate={}, endDate={}", symbol, startDate, endDate);

    List<DividendDto> dividends = dividendService.getDividendHistory(symbol, startDate, endDate);

    return ResponseEntity.status(HttpStatus.OK).body(dividends);
  }

  @Operation(
    summary = "종목 티커 목록 조회",
    description = "상장 중인 종목의 티커만 조회합니다. 검색 자동완성에 씁니다"
  )
  @ApiResponses(value = {
    @ApiResponse(
      responseCode = "200",
      description = "티커 목록 조회 성공",
      content = @Content(
        mediaType = "application/json",
        schema = @Schema(implementation = SymbolDto.class)
      )
    )
  })
  @GetMapping("/symbols")
  public ResponseEntity<List<SymbolDto>> getActiveSymbols() {
    log.debug("활성 종목 티커 조회 요청");

    List<SymbolDto> symbols = assetService.getActiveSymbols().stream()
      .map(SymbolDto::new)
      .toList();

    log.debug("활성 종목 티커 조회 완료: {} 개", symbols.size());
    return ResponseEntity.status(HttpStatus.OK).body(symbols);
  }

  @Operation(
    summary = "종목 목록과 현재가 조회 (페이지네이션)",
    description = "시스템에 등록된 종목의 기본 정보와 최신 종가를 페이지 단위로 조회합니다"
  )
  @ApiResponses(value = {
    @ApiResponse(
      responseCode = "200",
      description = "종목 목록과 현재가 조회 성공",
      content = @Content(
        mediaType = "application/json",
        schema = @Schema(implementation = StockPriceDto.class)
      )
    )
  })
  @GetMapping("/stocks")
  public ResponseEntity<org.springframework.data.domain.Page<StockPriceDto>> getAllStocksWithPrices(
      @Parameter(description = "페이지 번호 (0부터 시작)", example = "0")
      @RequestParam(defaultValue = "0") int page,
      @Parameter(description = "페이지 크기", example = "50")
      @RequestParam(defaultValue = "50") int size,
      @Parameter(description = "정렬 방향 (asc: 오름차순, desc: 내림차순) - 시가총액 기준", example = "desc")
      @RequestParam(defaultValue = "desc") String direction,
      @Parameter(description = "자산 유형 필터 (ALL, EQUITY, ETF)", example = "ALL")
      @RequestParam(required = false) String assetType) {
    log.debug("종목 목록과 현재가 조회 요청: page={}, size={}, direction={}, assetType={}",
        page, size, direction, assetType);

    org.springframework.data.domain.Pageable pageable =
        org.springframework.data.domain.PageRequest.of(page, size);
    org.springframework.data.domain.Page<StockPriceDto> stocks =
        candleService.getAllStocksWithPrices(pageable, direction, assetType);

    log.debug("종목 목록과 현재가 조회 완료: {} 개 (전체 {}개 중)", stocks.getNumberOfElements(), stocks.getTotalElements());
    return ResponseEntity.status(HttpStatus.OK).body(stocks);
  }

  @Operation(
    summary = "다중 종목 OHLC 데이터 조회",
    description = "여러 종목의 지정 기간 OHLC 데이터를 일괄 조회합니다"
  )
  @ApiResponses(value = {
    @ApiResponse(
      responseCode = "200",
      description = "다중 OHLC 데이터 조회 성공",
      content = @Content(
        mediaType = "application/json",
        schema = @Schema(implementation = OHLCPriceDto.class)
      )
    ),
    @ApiResponse(
      responseCode = "400",
      description = "잘못된 요청 (종목 수 초과 또는 잘못된 날짜 범위)"
    )
  })
  @GetMapping("/ohlc/multiple")
  public ResponseEntity<List<OHLCPriceDto>> getMultipleOHLCPrices(
    @Parameter(description = "종목 코드 목록 (1-50개)", example = "[\"AAPL\", \"MSFT\", \"GOOGL\"]", required = true)
    @RequestParam @Size(min = 1, max = 50) List<@Pattern(regexp = "^[A-Z]{1,16}$") String> symbols,
    @Parameter(description = "시작 날짜", example = "2024-01-01", required = true)
    @RequestParam @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate startDate,
    @Parameter(description = "종료 날짜", example = "2024-12-31", required = true)
    @RequestParam @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate endDate) {
    log.debug("다중 OHLC 조회 요청: symbols={}, startDate={}, endDate={}", symbols, startDate, endDate);

    List<OHLCPriceDto> result = candleService.getMultipleOHLCPrices(symbols, startDate, endDate);

    return ResponseEntity.status(HttpStatus.OK).body(result);
  }

  @Operation(
    summary = "배당 정보 포함 OHLC 데이터 조회",
    description = "지정된 종목의 OHLC 데이터와 배당 정보를 함께 조회합니다"
  )
  @ApiResponses(value = {
    @ApiResponse(
      responseCode = "200",
      description = "배당 포함 OHLC 데이터 조회 성공",
      content = @Content(
        mediaType = "application/json",
        schema = @Schema(implementation = OHLCPriceDto.class)
      )
    ),
    @ApiResponse(
      responseCode = "404",
      description = "종목을 찾을 수 없음"
    )
  })
  @GetMapping("/ohlc/{symbol}/with-dividends")
  public ResponseEntity<List<OHLCPriceDto>> getCandlesWithDividends(
    @Parameter(description = "종목 코드 (예: AAPL, MSFT)", example = "AAPL", required = true)
    @PathVariable @NotBlank @Pattern(regexp = "^[A-Z]{1,16}$") String symbol,
    @Parameter(description = "시작 날짜", example = "2024-01-01", required = true)
    @RequestParam @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate startDate,
    @Parameter(description = "종료 날짜", example = "2024-12-31", required = true)
    @RequestParam @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate endDate) {
    log.debug("배당 포함 캔들 조회 요청: symbol={}, startDate={}, endDate={}", symbol, startDate, endDate);

    List<OHLCPriceDto> result = candleService.getCandlesWithDividends(symbol, startDate, endDate);

    return ResponseEntity.status(HttpStatus.OK).body(result);
  }

  @Operation(
    summary = "고배당주 검색",
    description = "지정된 최소 배당금 이상의 고배당주를 검색합니다"
  )
  @ApiResponses(value = {
    @ApiResponse(
      responseCode = "200",
      description = "고배당주 검색 성공",
      content = @Content(
        mediaType = "application/json",
        schema = @Schema(implementation = DividendDto.class)
      )
    ),
    @ApiResponse(
      responseCode = "400",
      description = "잘못된 배당금 값"
    )
  })
  @GetMapping("/dividend/high-yield")
  public ResponseEntity<List<DividendDto>> findHighDividendStocks(
    @Parameter(description = "최소 배당금 ($)", example = "1.0", required = true)
    @RequestParam @Positive BigDecimal minAmount,
    @Parameter(description = "검색 시작 날짜 (기본: 1년 전)", example = "2024-01-01")
    @RequestParam(required = false) @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate fromDate) {
    log.debug("고배당주 검색 요청: minAmount={}, fromDate={}", minAmount, fromDate);

    LocalDate searchFromDate = fromDate != null ? fromDate : LocalDate.now().minusYears(1);
    List<DividendDto> result = dividendService.findHighDividendStocks(minAmount, searchFromDate);

    return ResponseEntity.status(HttpStatus.OK).body(result);
  }

  @Operation(
    summary = "Bulk 환율 조회",
    description = "여러 날짜의 USD/KRW 환율을 한 번에 조회합니다. 백테스트 차트 등에서 사용됩니다. URL 길이 제한을 피하기 위해 POST 요청을 사용합니다."
  )
  @ApiResponses(value = {
    @ApiResponse(
      responseCode = "200",
      description = "Bulk 환율 조회 성공"
    ),
    @ApiResponse(
      responseCode = "400",
      description = "잘못된 요청"
    )
  })
  @PostMapping("/fx/bulk")
  public ResponseEntity<Map<String, BigDecimal>> getBulkFxRates(
    @Parameter(description = "조회할 날짜 리스트 (YYYY-MM-DD)", required = true)
    @org.springframework.web.bind.annotation.RequestBody List<String> dates) {

    log.debug("Bulk 환율 조회 요청: dates size={}", dates.size());

    // String을 LocalDate로 변환
    List<LocalDate> localDates = dates.stream()
      .map(LocalDate::parse)
      .toList();

    List<FxRate> fxRates = fxRateService.findByDates(localDates);

    // Map으로 변환: 날짜 문자열 -> 환율
    Map<String, BigDecimal> result = new java.util.HashMap<>();
    for (FxRate rate : fxRates) {
      result.put(rate.getDate().toString(), rate.getRate());
    }

    log.debug("Bulk 환율 조회 완료: {}개 요청, {}개 반환", dates.size(), result.size());

    return ResponseEntity.status(HttpStatus.OK).body(result);
  }

}
