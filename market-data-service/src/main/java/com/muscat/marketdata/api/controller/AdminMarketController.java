package com.muscat.marketdata.api.controller;

import com.muscat.marketdata.datasource.yf.collector.SymbolCollector;
import com.muscat.marketdata.domain.dto.AssetSummaryDto;
import com.muscat.marketdata.domain.entity.Asset;
import com.muscat.marketdata.domain.service.AssetService;
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

/**
 * 관리자용 마켓 데이터 관리 API
 * 심볼 추가/삭제, 데이터 수집 등의 관리 기능
 */
@Slf4j
@RestController
@RequestMapping("/api/admin/market")
@RequiredArgsConstructor
public class AdminMarketController {

    private final AssetService assetService;

    // SymbolCollector 는 marketdata.provider=yahoo 일 때만 뜬다.
    // 직접 주입하면 alphavantage 프로파일에서 기동이 깨지므로 선택 주입한다.
    private final ObjectProvider<SymbolCollector> symbolCollectorProvider;

    /**
     * 키워드로 심볼 검색
     *
     * GET /api/admin/market/assets/search?keyword=Tesla
     */
    @GetMapping("/assets/search")
    public ResponseEntity<List<Asset>> searchSymbols(@RequestParam String keyword) {
        log.info("심볼 검색 요청: keyword={}", keyword);
        List<Asset> results = assetService.searchSymbols(keyword);
        return ResponseEntity.ok(results);
    }

    /**
     * 심볼로 회사 정보 미리보기
     *
     * GET /api/admin/market/assets/preview/AAPL
     */
    @GetMapping("/assets/preview/{symbol}")
    public ResponseEntity<Asset> previewSymbol(@PathVariable String symbol) {
        log.info("심볼 미리보기 요청: symbol={}", symbol);
        return assetService.previewSymbol(symbol)
                .map(ResponseEntity::ok)
                .orElse(ResponseEntity.notFound().build());
    }

    /**
     * 전체 심볼 목록 조회 (기본 정보)
     *
     * GET /api/admin/market/assets
     */
    @GetMapping("/assets")
    public ResponseEntity<List<Asset>> getAllAssets() {
        log.info("전체 심볼 목록 조회");
        List<Asset> assets = assetService.getAllAssets();
        return ResponseEntity.ok(assets);
    }

    /**
     * 전체 심볼 요약 정보 조회 (가격, 최신 데이터 날짜 포함, 페이지네이션)
     *
     * GET /api/admin/market/assets/summary?page=0&size=20&sort=symbol,asc
     */
    @GetMapping("/assets/summary")
    public ResponseEntity<PageResponse<AssetSummaryDto>> getAllAssetSummaries(
            @RequestParam(defaultValue = "0") int page,
            @RequestParam(defaultValue = "20") int size,
            @RequestParam(defaultValue = "symbol") String sortBy,
            @RequestParam(defaultValue = "asc") String direction) {

        log.info("전체 심볼 요약 정보 조회 (page={}, size={}, sort={},{})",
                page, size, sortBy, direction);

        Sort.Direction sortDirection = direction.equalsIgnoreCase("desc")
                ? Sort.Direction.DESC : Sort.Direction.ASC;
        Pageable pageable = PageRequest.of(page, size, Sort.by(sortDirection, sortBy));

        Page<AssetSummaryDto> summaries = assetService.getAllAssetSummaries(pageable);
        return ResponseEntity.ok(PageResponse.from(summaries));
    }

    /**
     * 특정 심볼 조회
     *
     * GET /api/admin/market/assets/AAPL
     */
    @GetMapping("/assets/{symbol}")
    public ResponseEntity<Asset> getAsset(@PathVariable String symbol) {
        log.info("심볼 조회: symbol={}", symbol);
        return assetService.getAsset(symbol)
                .map(ResponseEntity::ok)
                .orElse(ResponseEntity.notFound().build());
    }

    /**
     * 새로운 심볼 추가
     *
     * POST /api/admin/market/assets
     * {
     *   "symbol": "AAPL",
     *   "name": "Apple Inc",  // optional (null이면 Alpha Vantage에서 자동 조회)
     *   "collectData": true,
     *   "fromDate": "2023-01-01",
     *   "toDate": "2025-10-22",
     *   "includeDividends": true
     * }
     */
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

        } catch (IllegalArgumentException e) {
            log.warn("심볼 추가 실패: {}", e.getMessage());
            return ResponseEntity.badRequest().body(AssetResponse.builder()
                    .message(e.getMessage())
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
        } catch (IllegalArgumentException e) {
            log.warn("종목 이름 수정 실패: {}", e.getMessage());
            HttpStatus status = e.getMessage() != null && e.getMessage().startsWith("Not found")
                    ? HttpStatus.NOT_FOUND : HttpStatus.BAD_REQUEST;
            return ResponseEntity.status(status)
                    .body(AssetResponse.builder().message(e.getMessage()).build());
        }
    }

    /**
     * 심볼 삭제 (soft delete)
     *
     * DELETE /api/admin/market/assets/AAPL
     */
    @DeleteMapping("/assets/{symbol}")
    public ResponseEntity<Void> deleteAsset(@PathVariable String symbol) {
        log.info("심볼 삭제 요청: symbol={}", symbol);

        try {
            assetService.deleteAsset(symbol);
            return ResponseEntity.noContent().build();
        } catch (IllegalArgumentException e) {
            log.warn("심볼 삭제 실패: {}", e.getMessage());
            return ResponseEntity.notFound().build();
        }
    }

    /**
     * 여러 종목 일괄 삭제 (soft delete)
     *
     * POST /api/admin/market/assets/bulk-delete
     */
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

    /**
     * 특정 종목의 가격 데이터 업데이트
     *
     * POST /api/admin/market/assets/AAPL/update-price
     */
    @PostMapping("/assets/{symbol}/update-price")
    public ResponseEntity<UpdateResponse> updateAssetPrice(@PathVariable String symbol) {
        log.info("종목 가격 업데이트 요청: symbol={}", symbol);

        try {
            assetService.updateAssetPrice(symbol);
            return ResponseEntity.ok(UpdateResponse.builder()
                    .message("종목 가격 데이터가 업데이트되었습니다: " + symbol)
                    .build());
        } catch (IllegalArgumentException e) {
            log.warn("종목 가격 업데이트 실패: {}", e.getMessage());
            return ResponseEntity.badRequest()
                    .body(UpdateResponse.builder()
                            .message("종목을 찾을 수 없습니다: " + symbol)
                            .build());
        } catch (IllegalStateException e) {
            log.warn("종목 가격 업데이트 실패: {}", e.getMessage());
            return ResponseEntity.status(HttpStatus.SERVICE_UNAVAILABLE)
                    .body(UpdateResponse.builder()
                            .message(e.getMessage())
                            .build());
        } catch (Exception e) {
            log.error("종목 가격 업데이트 실패", e);
            return ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR)
                    .body(UpdateResponse.builder()
                            .message("가격 업데이트 실패: " + e.getMessage())
                            .build());
        }
    }

    /**
     * 특정 종목의 시가총액 업데이트
     *
     * POST /api/admin/market/assets/AAPL/update-market-cap
     */
    @PostMapping("/assets/{symbol}/update-market-cap")
    public ResponseEntity<UpdateResponse> updateAssetMarketCap(@PathVariable String symbol) {
        log.info("종목 시가총액 업데이트 요청: symbol={}", symbol);

        try {
            assetService.updateAssetMarketCap(symbol);
            return ResponseEntity.ok(UpdateResponse.builder()
                    .message("종목 시가총액이 업데이트되었습니다: " + symbol)
                    .build());
        } catch (IllegalArgumentException e) {
            log.warn("종목 시가총액 업데이트 실패: {}", e.getMessage());
            return ResponseEntity.badRequest()
                    .body(UpdateResponse.builder()
                            .message("종목을 찾을 수 없습니다: " + symbol)
                            .build());
        } catch (IllegalStateException e) {
            log.warn("종목 시가총액 업데이트 실패: {}", e.getMessage());
            return ResponseEntity.status(HttpStatus.SERVICE_UNAVAILABLE)
                    .body(UpdateResponse.builder()
                            .message(e.getMessage())
                            .build());
        } catch (Exception e) {
            log.error("종목 시가총액 업데이트 실패", e);
            return ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR)
                    .body(UpdateResponse.builder()
                            .message("시가총액 업데이트 실패: " + e.getMessage())
                            .build());
        }
    }

    /**
     * 시가총액 업데이트 (수동 트리거 - 전체)
     *
     * POST /api/admin/market/update/market-cap
     */
    @PostMapping("/update/market-cap")
    public ResponseEntity<UpdateResponse> updateMarketCaps() {
        log.info("시가총액 수동 업데이트 요청");

        try {
            new Thread(() -> {
                try {
                    int updated = assetService.updateAllMarketCaps();
                    log.info("시가총액 전체 업데이트 완료: {}개", updated);
                } catch (Exception e) {
                    log.error("시가총액 전체 업데이트 실패", e);
                }
            }).start();

            return ResponseEntity.ok(UpdateResponse.builder()
                    .message("시가총액 업데이트가 백그라운드에서 시작되었습니다.")
                    .build());
        } catch (Exception e) {
            log.error("시가총액 업데이트 요청 실패", e);
            return ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR)
                    .body(UpdateResponse.builder()
                            .message("시가총액 업데이트 요청 실패: " + e.getMessage())
                            .build());
        }
    }

    /**
     * 캔들 데이터 업데이트 (수동 트리거)
     *
     * POST /api/admin/market/update/candles
     */
    @PostMapping("/update/candles")
    public ResponseEntity<UpdateResponse> updateCandles() {
        log.info("캔들 데이터 수동 업데이트 요청");

        try {
            new Thread(() -> {
                List<Asset> assets = assetService.getAllAssets();
                for (Asset asset : assets) {
                    try {
                        assetService.updateAssetPrice(asset.getSymbol());
                    } catch (Exception e) {
                        log.warn("캔들 데이터 업데이트 실패: symbol={}", asset.getSymbol(), e);
                    }
                }
                log.info("캔들 전체 업데이트 완료: {}개", assets.size());
            }).start();

            return ResponseEntity.ok(UpdateResponse.builder()
                    .message("캔들 데이터 업데이트가 백그라운드에서 시작되었습니다.")
                    .build());
        } catch (Exception e) {
            log.error("캔들 데이터 업데이트 요청 실패", e);
            return ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR)
                    .body(UpdateResponse.builder()
                            .message("캔들 데이터 업데이트 요청 실패: " + e.getMessage())
                            .build());
        }
    }

    /**
     * 신규 상장 종목 수집 (수동 트리거)
     *
     * POST /api/admin/market/update/symbols
     *
     * 평일 08:00 스케줄과 같은 일을 한다. 목록을 다시 받아 DB 에 없는 심볼만 추가한다.
     * 기존 종목은 건드리지 않는다.
     *
     * 필터를 조정한 뒤 결과를 바로 보고 싶을 때 쓴다. 스케줄을 기다리거나
     * 파드를 재시작할 필요가 없다.
     */
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

        try {
            new Thread(() -> {
                try {
                    collector.collectNewlyListedDaily();
                } catch (Exception e) {
                    log.error("신규 종목 수집 실패", e);
                }
            }).start();

            return ResponseEntity.ok(UpdateResponse.builder()
                    .message("신규 종목 수집이 백그라운드에서 시작되었습니다.")
                    .build());
        } catch (Exception e) {
            log.error("신규 종목 수집 요청 실패", e);
            return ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR)
                    .body(UpdateResponse.builder()
                            .message("신규 종목 수집 요청 실패: " + e.getMessage())
                            .build());
        }
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
}
