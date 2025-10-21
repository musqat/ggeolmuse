package com.muscat.marketdata.api;

import com.muscat.marketdata.domain.dto.OHLCPriceDto;
import com.muscat.marketdata.domain.dto.StockPriceDto;
import com.muscat.marketdata.domain.entity.Asset;
import com.muscat.marketdata.domain.entity.FxRate;
import com.muscat.marketdata.domain.service.MarketService;
import com.muscat.marketdata.feed.service.FxRateService;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.Parameter;
import io.swagger.v3.oas.annotations.media.Content;
import io.swagger.v3.oas.annotations.media.Schema;
import io.swagger.v3.oas.annotations.responses.ApiResponse;
import io.swagger.v3.oas.annotations.responses.ApiResponses;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.constraints.Pattern;
import jakarta.validation.constraints.Size;
import java.time.LocalDate;
import java.util.List;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.format.annotation.DateTimeFormat;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.validation.annotation.Validated;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

@Slf4j
@RestController
@Validated
@RequestMapping("/api/market/public")
@RequiredArgsConstructor
@Tag(name = "Public 시장 데이터", description = "인증 없이 접근 가능한 공개 시장 데이터 API")
public class PublicMarketController {

  private final MarketService marketService;
  private final FxRateService fxRateService;

  @Operation(
    summary = "전체 종목 목록 조회 (Public)",
    description = "테스트용 하드코딩된 종목 목록을 반환합니다 (AAPL, MSFT, GOOGL, TSLA, NVDA)"
  )
  @ApiResponses(value = {
    @ApiResponse(
      responseCode = "200",
      description = "종목 목록 조회 성공",
      content = @Content(
        mediaType = "application/json",
        schema = @Schema(implementation = Asset.class)
      )
    )
  })
  @GetMapping("/symbols")
  public ResponseEntity<List<Asset>> getAllSymbols() {
    log.debug("[Public API] 전체 종목 목록 조회 요청 (하드코딩된 5개 종목)");

    // 하드코딩된 테스트용 종목 목록
    List<Asset> hardcodedAssets = List.of(
      Asset.builder()
        .symbol("AAPL")
        .name("Apple Inc.")
        .country("US")
        .currency("USD")
        .assetType("EQUITY")
        .build(),
      Asset.builder()
        .symbol("MSFT")
        .name("Microsoft Corp.")
        .country("US")
        .currency("USD")
        .assetType("EQUITY")
        .build(),
      Asset.builder()
        .symbol("GOOGL")
        .name("Alphabet Inc.")
        .country("US")
        .currency("USD")
        .assetType("EQUITY")
        .build(),
      Asset.builder()
        .symbol("TSLA")
        .name("Tesla Inc.")
        .country("US")
        .currency("USD")
        .assetType("EQUITY")
        .build(),
      Asset.builder()
        .symbol("NVDA")
        .name("NVIDIA Corp.")
        .country("US")
        .currency("USD")
        .assetType("EQUITY")
        .build()
    );

    return ResponseEntity.status(HttpStatus.OK).body(hardcodedAssets);
  }

  @Operation(
    summary = "종목 목록과 현재가 조회 (Public)",
    description = "시스템에 등록된 모든 종목의 기본 정보와 현재가를 조회합니다"
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
  public ResponseEntity<List<StockPriceDto>> getAllStocksWithPrices() {
    log.debug("[Public API] 종목 목록과 현재가 조회 요청");

    List<StockPriceDto> stocks = marketService.getAllStocksWithPrices();

    return ResponseEntity.status(HttpStatus.OK).body(stocks);
  }

  @Operation(
    summary = "다중 종목 OHLC 데이터 조회 (Public)",
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
    log.debug("[Public API] 다중 OHLC 조회 요청: symbols={}, startDate={}, endDate={}", symbols,
      startDate, endDate);

    List<OHLCPriceDto> result = marketService.getMultipleOHLCPrices(symbols, startDate, endDate);

    return ResponseEntity.status(HttpStatus.OK).body(result);
  }

  @Operation(
    summary = "Bulk 환율 조회 (Public)",
    description = "여러 날짜의 USD/KRW 환율을 한 번에 조회합니다. 백테스트 차트에서 사용됩니다. URL 길이 제한을 피하기 위해 POST 요청을 사용합니다."
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
  public ResponseEntity<java.util.Map<String, java.math.BigDecimal>> getBulkFxRates(
    @Parameter(description = "조회할 날짜 리스트 (YYYY-MM-DD)", required = true)
    @org.springframework.web.bind.annotation.RequestBody List<String> dates) {

    log.debug("[Public API] Bulk 환율 조회 요청: dates size={}", dates.size());

    // String을 LocalDate로 변환
    List<LocalDate> localDates = dates.stream()
      .map(LocalDate::parse)
      .toList();

    List<FxRate> fxRates = fxRateService.findByDates(localDates);

    // Map으로 변환: 날짜 문자열 -> 환율
    java.util.Map<String, java.math.BigDecimal> result = new java.util.HashMap<>();
    for (FxRate rate : fxRates) {
      result.put(rate.getDate().toString(), rate.getRate());
    }

    log.debug("[Public API] Bulk 환율 조회 완료: {}개 요청, {}개 반환", dates.size(), result.size());

    return ResponseEntity.status(HttpStatus.OK).body(result);
  }
}
