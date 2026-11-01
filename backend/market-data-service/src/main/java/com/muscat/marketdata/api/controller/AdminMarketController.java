package com.muscat.marketdata.api.controller;

import com.muscat.marketdata.datasource.yf.collector.SymbolCollector;
import com.muscat.marketdata.domain.dto.AssetSummaryDto;
import com.muscat.marketdata.domain.dto.admin.AssetResponse;
import com.muscat.marketdata.domain.dto.admin.BulkDeleteRequest;
import com.muscat.marketdata.domain.dto.admin.BulkDeleteResponse;
import com.muscat.marketdata.domain.dto.admin.CreateAssetRequest;
import com.muscat.marketdata.domain.dto.admin.FxCollectResponse;
import com.muscat.marketdata.domain.dto.admin.PageResponse;
import com.muscat.marketdata.domain.dto.admin.RefreshAllResponse;
import com.muscat.marketdata.domain.dto.admin.RefreshRequest;
import com.muscat.marketdata.domain.dto.admin.RefreshResponse;
import com.muscat.marketdata.domain.dto.admin.UnadjustedResponse;
import com.muscat.marketdata.domain.dto.admin.UpdateAssetRequest;
import com.muscat.marketdata.domain.dto.admin.UpdateResponse;
import com.muscat.marketdata.domain.entity.Asset;
import com.muscat.marketdata.domain.service.AssetPriceBatchUpdater;
import com.muscat.marketdata.domain.service.AssetService;
import com.muscat.marketdata.domain.service.CandleRefreshService;
import com.muscat.marketdata.domain.service.FxCollectionService;
import com.muscat.marketdata.domain.service.UnadjustedScanService;
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
import java.util.List;
import java.util.Locale;

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
    private final FxCollectionService fxCollectionService;
    private final AdminJobRunner jobRunner;

    // yahoo 프로바이더에서만 뜨는 빈이라 선택 주입
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

        String message = request.isCollectData() ? "심볼이 추가되었고 데이터 수집이 시작되었습니다." : "심볼이 추가되었습니다.";
        return ResponseEntity.status(HttpStatus.CREATED).body(AssetResponse.of(asset, message));
    }

    // 종목 이름 수정(잘못 수집된 회사명 교정)
    @PutMapping("/assets/{symbol}")
    public ResponseEntity<AssetResponse> updateAsset(
            @PathVariable String symbol,
            @RequestBody UpdateAssetRequest request) {
        log.info("종목 이름 수정 요청: symbol={}, name={}", symbol, request.getName());

        Asset asset = assetService.updateAssetName(symbol, request.getName());
        return ResponseEntity.ok(AssetResponse.of(asset, "종목 이름이 수정되었습니다."));
    }

    // 비활성으로 돌림(soft delete)
    @DeleteMapping("/assets/{symbol}")
    public ResponseEntity<Void> deleteAsset(@PathVariable String symbol) {
        log.info("심볼 삭제 요청: symbol={}", symbol);

        assetService.deleteAsset(symbol);
        return ResponseEntity.noContent().build();
    }

    @PostMapping("/assets/{symbol}/restore")
    public ResponseEntity<Void> restoreAsset(@PathVariable String symbol) {
        log.info("심볼 복구 요청: symbol={}", symbol);

        assetService.restoreAsset(symbol);
        return ResponseEntity.noContent().build();
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

        assetService.updateAssetPrice(symbol);
        return ResponseEntity.ok(UpdateResponse.of("종목 가격 데이터가 업데이트되었습니다: " + symbol));
    }

    @PostMapping("/assets/{symbol}/update-market-cap")
    public ResponseEntity<UpdateResponse> updateAssetMarketCap(@PathVariable String symbol) {
        log.info("종목 시가총액 업데이트 요청: symbol={}", symbol);

        assetService.updateAssetMarketCap(symbol);
        return ResponseEntity.ok(UpdateResponse.of("종목 시가총액이 업데이트되었습니다: " + symbol));
    }

    @PostMapping("/update/market-cap")
    public ResponseEntity<UpdateResponse> updateMarketCaps() {
        log.info("시가총액 수동 업데이트 요청");

        boolean started = jobRunner.submit("시가총액 전체 갱신", assetService::updateAllMarketCaps);
        if (!started) {
            return jobBusy();
        }

        return ResponseEntity.ok(UpdateResponse.of("시가총액 업데이트가 백그라운드에서 시작되었습니다."));
    }

    @PostMapping("/update/candles")
    public ResponseEntity<UpdateResponse> updateCandles() {
        log.info("캔들 데이터 수동 업데이트 요청");

        boolean started = jobRunner.submit("캔들 전체 갱신", priceBatchUpdater::updateAllPrices);
        if (!started) {
            return jobBusy();
        }

        return ResponseEntity.ok(UpdateResponse.of("캔들 데이터 업데이트가 백그라운드에서 시작되었습니다."));
    }

    // 평일 08:00 스케줄과 같은 일(DB 에 없는 심볼만 추가, 기존 종목은 그대로)
    @PostMapping("/update/symbols")
    public ResponseEntity<UpdateResponse> collectNewSymbols() {
        log.info("신규 종목 수동 수집 요청");

        SymbolCollector collector = symbolCollectorProvider.getIfAvailable();
        if (collector == null) {
            return ResponseEntity.status(HttpStatus.SERVICE_UNAVAILABLE)
                    .body(UpdateResponse.of("신규 종목 수집은 yahoo 프로바이더에서만 지원합니다."));
        }

        boolean started = jobRunner.submit("신규 종목 수집", collector::collectNewlyListedDaily);
        if (!started) {
            return jobBusy();
        }

        return ResponseEntity.ok(UpdateResponse.of("신규 종목 수집이 백그라운드에서 시작되었습니다."));
    }

    private ResponseEntity<UpdateResponse> jobBusy() {
        log.warn("관리자 전체 갱신이 이미 돌고 하나가 기다리는 중이라 받지 않음");
        return ResponseEntity.status(HttpStatus.CONFLICT)
                .body(UpdateResponse.of("이미 다른 전체 갱신이 진행 중입니다. 끝난 뒤 다시 시도해 주세요."));
    }

    // 분할 계수가 기록된 종목(탐색이 몇 분 걸려 마지막 결과만)
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

    // 분할 종목 탐색 시작(결과는 GET 으로)
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

    // 환율 빈 날과 진행 중이거나 마지막 수집
    @GetMapping("/fx/collect")
    public ResponseEntity<FxCollectResponse> fxCollectStatus() {
        return ResponseEntity.ok(FxCollectResponse.of(fxCollectionService.status()));
    }

    // 환율 빠진 날 채우기(missing) · 전체 다시 받기(all), 백그라운드 실행 · 진행은 GET 으로
    @PostMapping("/fx/collect")
    public ResponseEntity<FxCollectResponse> startFxCollect(@RequestParam(defaultValue = "missing") String mode) {
        FxCollectionService.Mode parsed = switch (mode.toLowerCase(Locale.ROOT)) {
            case "missing" -> FxCollectionService.Mode.MISSING;
            case "all" -> FxCollectionService.Mode.ALL;
            default -> null;
        };
        if (parsed == null) {
            return ResponseEntity.badRequest().body(FxCollectResponse.message("모드는 missing 또는 all 입니다."));
        }

        boolean started = fxCollectionService.start(parsed);
        log.info("환율 수집 요청: mode={}, started={}", parsed, started);

        FxCollectResponse body = FxCollectResponse.of(fxCollectionService.status());
        if (!started) {
            body.setMessage("이미 환율 수집이 진행 중입니다. 끝난 뒤 다시 시도해 주세요.");
            return ResponseEntity.status(HttpStatus.CONFLICT).body(body);
        }
        return ResponseEntity.accepted().body(body);
    }
}
