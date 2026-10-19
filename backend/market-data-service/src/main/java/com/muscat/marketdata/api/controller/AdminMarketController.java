package com.muscat.marketdata.api.controller;

import com.muscat.marketdata.common.exceptions.MarketDataException;
import com.muscat.marketdata.datasource.yf.collector.SymbolCollector;
import com.muscat.marketdata.domain.dto.AssetSummaryDto;
import com.muscat.marketdata.domain.entity.AdminJobRun;
import com.muscat.marketdata.domain.entity.Asset;
import com.muscat.marketdata.domain.service.AssetPriceBatchUpdater;
import com.muscat.marketdata.domain.service.AssetService;
import com.muscat.marketdata.domain.service.CandleRefreshService;
import com.muscat.marketdata.domain.service.UnadjustedScanService;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.ObjectProvider;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Sort;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.List;

@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
class PageResponse<T> {
    private List<T> content;
    private int number;
    private int size;
    private long totalElements;
    private int totalPages;
    private boolean first;
    private boolean last;

    static <T> PageResponse<T> from(Page<T> page) {
        return PageResponse.<T>builder()
            .content(page.getContent())
            .number(page.getNumber())
            .size(page.getSize())
            .totalElements(page.getTotalElements())
            .totalPages(page.getTotalPages())
            .first(page.isFirst())
            .last(page.isLast())
            .build();
    }
}

// 관리자용 종목 · 시세 관리 API
@Slf4j
@RestController
@RequestMapping("/api/admin/market")
@RequiredArgsConstructor
public class AdminMarketController {

    private final AssetService assetService;
    private final AssetPriceBatchUpdater priceBatchUpdater;
    private final UnadjustedScanService scanService;
    private final CandleRefreshService candleRefreshService;
    private final AdminJobRunner jobRunner;

    // SymbolCollector 는 marketdata.provider=yahoo 일 때만 뜬다.
    // 직접 주입하면 alphavantage 프로파일에서 기동이 깨지므로 선택 주입한다.
    private final ObjectProvider<SymbolCollector> symbolCollectorProvider;

    @GetMapping("/assets/search")
    public ResponseEntity<List<Asset>> searchSymbols(@RequestParam String keyword) {
        log.info("심볼 검색 요청: keyword={}", keyword);
        List<Asset> results = assetService.searchSymbols(keyword);
        return ResponseEntity.ok(results);
    }

    @GetMapping("/assets/preview/{symbol}")
    public ResponseEntity<Asset> previewSymbol(@PathVariable String symbol) {
        log.info("심볼 미리보기 요청: symbol={}", symbol);
        return assetService.previewSymbol(symbol)
                .map(ResponseEntity::ok)
                .orElse(ResponseEntity.notFound().build());
    }

    @GetMapping("/assets")
    public ResponseEntity<List<Asset>> getAllAssets() {
        log.info("전체 심볼 목록 조회");
        List<Asset> assets = assetService.getAllAssets();
        return ResponseEntity.ok(assets);
    }

    @GetMapping("/assets/summary")
    public ResponseEntity<PageResponse<AssetSummaryDto>> getAllAssetSummaries(
            @RequestParam(defaultValue = "0") int page,
            @RequestParam(defaultValue = "20") int size,
            @RequestParam(defaultValue = "symbol") String sortBy,
            @RequestParam(defaultValue = "asc") String direction,
            @RequestParam(defaultValue = "true") boolean active) {

        log.info("심볼 요약 조회 (active={}, page={}, size={}, sort={},{})",
                active, page, size, sortBy, direction);

        Sort.Direction sortDirection = direction.equalsIgnoreCase("desc")
                ? Sort.Direction.DESC : Sort.Direction.ASC;
        Pageable pageable = PageRequest.of(page, size, Sort.by(sortDirection, sortBy));

        Page<AssetSummaryDto> summaries = assetService.getAllAssetSummaries(pageable, active);
        return ResponseEntity.ok(PageResponse.from(summaries));
    }

    @GetMapping("/assets/{symbol}")
    public ResponseEntity<Asset> getAsset(@PathVariable String symbol) {
        log.info("심볼 조회: symbol={}", symbol);
        return assetService.getAsset(symbol)
                .map(ResponseEntity::ok)
                .orElse(ResponseEntity.notFound().build());
    }

    // 이름이 비면 공급원에서 채움
    @PostMapping("/assets")
    public ResponseEntity<AssetResponse> createAsset(@RequestBody CreateAssetRequest request) {
        log.info("심볼 추가 요청: symbol={}, collectData={}", request.getSymbol(), request.isCollectData());

        try {
            Asset asset = assetService.createAsset(
                    request.getSymbol(),
                    request.getName(),
                    request.getCountry(),
                    request.getCurrency(),
                    request.getAssetType(),
                    request.isCollectData(),
                    request.getFromDate(),
                    request.getToDate(),
                    request.isIncludeDividends()
            );

            AssetResponse response = AssetResponse.builder()
                    .symbol(asset.getSymbol())
                    .name(asset.getName())
                    .country(asset.getCountry())
                    .currency(asset.getCurrency())
                    .assetType(asset.getAssetType())
                    .message(request.isCollectData()
                            ? "심볼이 추가되었고 데이터 수집이 시작되었습니다."
                            : "심볼이 추가되었습니다.")
                    .build();

            return ResponseEntity.status(HttpStatus.CREATED).body(response);

        } catch (MarketDataException e) {
            log.warn("심볼 추가 실패: symbol={}, code={}", request.getSymbol(), e.getErrorCode());
            return ResponseEntity.status(e.getHttpStatus()).body(AssetResponse.builder()
                    .message(e.getErrorMessage() + ": " + request.getSymbol().toUpperCase())
                    .build());
        }
    }

    /**
     * 종목 이름 수정 (잘못 수집된 회사명 교정)
     */
    @PutMapping("/assets/{symbol}")
    public ResponseEntity<AssetResponse> updateAsset(
            @PathVariable String symbol,
            @RequestBody UpdateAssetRequest request) {
        log.info("종목 이름 수정 요청: symbol={}, name={}", symbol, request.getName());

        try {
            Asset asset = assetService.updateAssetName(symbol, request.getName());
            return ResponseEntity.ok(AssetResponse.builder()
                    .symbol(asset.getSymbol())
                    .name(asset.getName())
                    .country(asset.getCountry())
                    .currency(asset.getCurrency())
                    .assetType(asset.getAssetType())
                    .message("종목 이름이 수정되었습니다.")
                    .build());
        } catch (MarketDataException e) {
            log.warn("종목 이름 수정 실패: symbol={}, code={}", symbol, e.getErrorCode());
            return ResponseEntity.status(e.getHttpStatus())
                    .body(AssetResponse.builder().message(e.getErrorMessage()).build());
        }
    }

    // 비활성으로 돌림(soft delete)
    @DeleteMapping("/assets/{symbol}")
    public ResponseEntity<Void> deleteAsset(@PathVariable String symbol) {
        log.info("심볼 삭제 요청: symbol={}", symbol);

        try {
            assetService.deleteAsset(symbol);
            return ResponseEntity.noContent().build();
        } catch (MarketDataException e) {
            log.warn("심볼 삭제 실패: symbol={}, code={}", symbol, e.getErrorCode());
            return ResponseEntity.status(e.getHttpStatus()).build();
        }
    }

    @PostMapping("/assets/{symbol}/restore")
    public ResponseEntity<Void> restoreAsset(@PathVariable String symbol) {
        log.info("심볼 복구 요청: symbol={}", symbol);

        try {
            assetService.restoreAsset(symbol);
            return ResponseEntity.noContent().build();
        } catch (MarketDataException e) {
            log.warn("심볼 복구 실패: symbol={}, code={}", symbol, e.getErrorCode());
            return ResponseEntity.status(e.getHttpStatus()).build();
        }
    }

    // 여러 종목을 비활성으로 돌림(soft delete)
    @PostMapping("/assets/bulk-delete")
    public ResponseEntity<BulkDeleteResponse> bulkDeleteAssets(@RequestBody BulkDeleteRequest request) {
        int size = request.getSymbols() == null ? 0 : request.getSymbols().size();
        log.info("심볼 일괄 삭제 요청: {}개", size);

        int deleted = assetService.deleteAssets(request.getSymbols());
        return ResponseEntity.ok(BulkDeleteResponse.builder()
                .requested(size)
                .deleted(deleted)
                .message(deleted + "개 종목이 비활성화되었습니다.")
                .build());
    }

    @PostMapping("/assets/{symbol}/update-price")
    public ResponseEntity<UpdateResponse> updateAssetPrice(@PathVariable String symbol) {
        log.info("종목 가격 업데이트 요청: symbol={}", symbol);

        try {
            assetService.updateAssetPrice(symbol);
            return ResponseEntity.ok(UpdateResponse.builder()
                    .message("종목 가격 데이터가 업데이트되었습니다: " + symbol)
                    .build());
        } catch (MarketDataException e) {
            log.warn("종목 가격 업데이트 실패: symbol={}, code={}, message={}",
                    symbol, e.getErrorCode(), e.getErrorMessage());
            return ResponseEntity.status(e.getHttpStatus())
                    .body(UpdateResponse.builder()
                            .message(e.getErrorMessage() + ": " + symbol)
                            .build());
        }
    }

    @PostMapping("/assets/{symbol}/update-market-cap")
    public ResponseEntity<UpdateResponse> updateAssetMarketCap(@PathVariable String symbol) {
        log.info("종목 시가총액 업데이트 요청: symbol={}", symbol);

        try {
            assetService.updateAssetMarketCap(symbol);
            return ResponseEntity.ok(UpdateResponse.builder()
                    .message("종목 시가총액이 업데이트되었습니다: " + symbol)
                    .build());
        } catch (MarketDataException e) {
            log.warn("종목 시가총액 업데이트 실패: symbol={}, code={}, message={}",
                    symbol, e.getErrorCode(), e.getErrorMessage());
            return ResponseEntity.status(e.getHttpStatus())
                    .body(UpdateResponse.builder()
                            .message(e.getErrorMessage() + ": " + symbol)
                            .build());
        }
    }

    @PostMapping("/update/market-cap")
    public ResponseEntity<UpdateResponse> updateMarketCaps() {
        log.info("시가총액 수동 업데이트 요청");

        boolean started = jobRunner.submit("시가총액 전체 갱신", assetService::updateAllMarketCaps);
        if (!started) {
            return jobBusy();
        }

        return ResponseEntity.ok(UpdateResponse.builder()
                .message("시가총액 업데이트가 백그라운드에서 시작되었습니다.")
                .build());
    }

    @PostMapping("/update/candles")
    public ResponseEntity<UpdateResponse> updateCandles() {
        log.info("캔들 데이터 수동 업데이트 요청");

        boolean started = jobRunner.submit("캔들 전체 갱신", priceBatchUpdater::updateAllPrices);
        if (!started) {
            return jobBusy();
        }

        return ResponseEntity.ok(UpdateResponse.builder()
                .message("캔들 데이터 업데이트가 백그라운드에서 시작되었습니다.")
                .build());
    }

    // 평일 08:00 스케줄과 같은 일. DB 에 없는 심볼만 추가하고 기존 종목은 그대로
    @PostMapping("/update/symbols")
    public ResponseEntity<UpdateResponse> collectNewSymbols() {
        log.info("신규 종목 수동 수집 요청");

        SymbolCollector collector = symbolCollectorProvider.getIfAvailable();
        if (collector == null) {
            // marketdata.provider 가 yahoo 가 아니면 이 수집기가 없다
            return ResponseEntity.status(HttpStatus.SERVICE_UNAVAILABLE)
                    .body(UpdateResponse.builder()
                            .message("신규 종목 수집은 yahoo 프로바이더에서만 지원합니다.")
                            .build());
        }

        boolean started = jobRunner.submit("신규 종목 수집", collector::collectNewlyListedDaily);
        if (!started) {
            return jobBusy();
        }

        return ResponseEntity.ok(UpdateResponse.builder()
                .message("신규 종목 수집이 백그라운드에서 시작되었습니다.")
                .build());
    }

    private ResponseEntity<UpdateResponse> jobBusy() {
        log.warn("관리자 전체 갱신이 이미 돌고 하나가 기다리는 중이라 받지 않음");
        return ResponseEntity.status(HttpStatus.CONFLICT)
                .body(UpdateResponse.builder()
                        .message("이미 다른 전체 갱신이 진행 중입니다. 끝난 뒤 다시 시도해 주세요.")
                        .build());
    }

    // ==================== DTO ====================

    @Data
    @NoArgsConstructor
    @AllArgsConstructor
    public static class CreateAssetRequest {
        private String symbol;
        private String name;      // optional
        private String country;   // optional
        private String currency;  // optional
        private String assetType; // optional

        private boolean collectData = false;
        private LocalDate fromDate;
        private LocalDate toDate;
        private boolean includeDividends = false;
    }

    @Data
    @NoArgsConstructor
    @AllArgsConstructor
    public static class UpdateAssetRequest {
        private String name;
    }

    @Data
    @Builder
    @NoArgsConstructor
    @AllArgsConstructor
    public static class AssetResponse {
        private String symbol;
        private String name;
        private String country;
        private String currency;
        private String assetType;
        private String message;
    }

    @Data
    @Builder
    @NoArgsConstructor
    @AllArgsConstructor
    public static class UpdateResponse {
        private String message;
    }

    @Data
    @NoArgsConstructor
    @AllArgsConstructor
    public static class BulkDeleteRequest {
        private List<String> symbols;
    }

    @Data
    @Builder
    @NoArgsConstructor
    @AllArgsConstructor
    public static class BulkDeleteResponse {
        private int requested;
        private int deleted;
        private String message;
    }

    // 분할 계수가 기록된 종목. 탐색이 몇 분 걸려 마지막 결과만 줌
    @GetMapping("/candles/unadjusted")
    public ResponseEntity<UnadjustedResponse> findUnadjusted() {
        UnadjustedScanService.Result last = scanService.getLast();

        if (last == null) {
            return ResponseEntity.ok(UnadjustedResponse.builder()
                .running(scanService.isRunning())
                .build());
        }

        return ResponseEntity.ok(UnadjustedResponse.builder()
            .running(scanService.isRunning())
            .from(last.getFrom())
            .count(last.getCount())
            .symbols(last.getSymbols())
            .finishedAt(last.getFinishedAt() != null ? last.getFinishedAt().toString() : null)
            .tookMillis(last.getTookMillis())
            .error(last.getError())
            .build());
    }

    // 분할 종목 탐색 시작. 결과는 GET 으로
    @PostMapping("/candles/unadjusted/scan")
    public ResponseEntity<UnadjustedResponse> scanUnadjusted(
        @RequestParam(defaultValue = "1970-01-01") LocalDate from) {

        boolean started = scanService.start(from);
        log.info("분할 종목 탐색 요청: from={}, started={}", from, started);

        return ResponseEntity.accepted().body(UnadjustedResponse.builder()
            .running(true)
            .from(from)
            .build());
    }

    @PostMapping("/candles/refresh")
    public ResponseEntity<RefreshResponse> refreshCandles(@RequestBody RefreshRequest request) {
        CandleRefreshService.RefreshResult result = candleRefreshService.refresh(request.getSymbols(), request.getFrom());
        return ResponseEntity.ok(RefreshResponse.builder()
            .requested(result.requested())
            .published(result.published())
            .notFound(result.notFound())
            .from(result.from())
            .to(result.to())
            .build());
    }

    @PostMapping("/candles/refresh-all")
    public ResponseEntity<RefreshAllResponse> refreshAllCandles() {
        return ResponseEntity.ok(RefreshAllResponse.of(candleRefreshService.refreshAll()));
    }

    @GetMapping("/candles/refresh-all")
    public ResponseEntity<RefreshAllResponse> lastRefreshAll() {
        return ResponseEntity.ok(candleRefreshService.lastRefreshAll()
            .map(RefreshAllResponse::of)
            .orElseGet(RefreshAllResponse::new));
    }

    @Data
    @Builder
    @NoArgsConstructor
    @AllArgsConstructor
    public static class UnadjustedResponse {
        private boolean running;
        private LocalDate from;
        private int count;
        private List<String> symbols;
        private String finishedAt;
        private long tookMillis;
        private String error;
    }

    @Data
    @NoArgsConstructor
    @AllArgsConstructor
    public static class RefreshRequest {
        private List<String> symbols;
        private LocalDate from;
    }

    @Data
    @Builder
    @NoArgsConstructor
    @AllArgsConstructor
    public static class RefreshResponse {
        private int requested;
        private int published;
        private List<String> notFound;
        private LocalDate from;
        private LocalDate to;
    }

    @Data
    @NoArgsConstructor
    @AllArgsConstructor
    public static class RefreshAllResponse {
        // 서울 시각
        private LocalDateTime lastRunAt;
        private int published;

        static RefreshAllResponse of(AdminJobRun run) {
            return new RefreshAllResponse(run.getLastRunAt(), run.getPublished());
        }
    }
}
