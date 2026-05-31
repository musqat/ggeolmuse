package com.muscat.marketdata.domain.service.impl;

import com.muscat.marketdata.datasource.common.MarketDataProvider.AssetInfoSource;
import com.muscat.marketdata.datasource.common.MarketDataProvider.CandleSource;
import com.muscat.marketdata.datasource.common.MarketDataProvider.MarketCapSource;
import com.muscat.marketdata.domain.dto.AssetSummaryDto;
import com.muscat.marketdata.domain.entity.Asset;
import com.muscat.marketdata.domain.entity.Candle;
import com.muscat.marketdata.domain.repository.AssetRepository;
import com.muscat.marketdata.domain.repository.CandleRepository;
import com.muscat.marketdata.domain.service.AssetService;
import com.muscat.marketdata.infra.kafka.AssetEventProducer;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.data.domain.Sort;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDate;
import java.util.List;
import java.util.Optional;
import java.util.stream.Collectors;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageImpl;
import org.springframework.data.domain.Pageable;

/**
 * Asset(자산/종목) 관리 서비스 구현체
 *
 * 종목 CRUD 및 조회 기능을 제공합니다.
 */
@Slf4j
@Service
public class AssetServiceImpl implements AssetService {

    private final AssetRepository assetRepository;
    private final CandleRepository candleRepository;
    private final AssetEventProducer assetEventProducer;

    // capability 인터페이스: @ConditionalOnProperty 로 활성 provider 1개만 주입됨
    // (yahoo → YfSymbolSource/YahooMarketCapService, alphavantage → SymbolSource/MarketCapCollectionService)
    @Autowired(required = false)
    private AssetInfoSource assetInfoSource;   // 단일 종목 상세 (preview/등록)

    @Autowired(required = false)
    private MarketCapSource marketCapSource;   // 시가총액 갱신

    @Autowired(required = false)
    private CandleSource candleSource;         // 단일 가격 갱신 (updateAssetPrice)

    public AssetServiceImpl(
            AssetRepository assetRepository,
            CandleRepository candleRepository,
            AssetEventProducer assetEventProducer) {
        this.assetRepository = assetRepository;
        this.candleRepository = candleRepository;
        this.assetEventProducer = assetEventProducer;
    }

    @Override
    @Transactional(readOnly = true)
    public Optional<Asset> previewSymbol(String symbol) {
        log.debug("심볼 미리보기 요청: symbol={}", symbol);

        if (assetInfoSource == null) {
            log.warn("AssetInfoSource가 활성화되지 않음");
            return Optional.empty();
        }

        Asset asset = assetInfoSource.getAsset(symbol.toUpperCase());
        if (asset != null) {
            log.debug("심볼 미리보기 성공: symbol={}, name={}", symbol, asset.getName());
        } else {
            log.warn("심볼 미리보기 실패: symbol={}", symbol);
        }

        return Optional.ofNullable(asset);
    }

    @Override
    @Transactional(readOnly = true)
    public List<Asset> searchSymbols(String keyword) {
        log.debug("심볼 검색 요청: keyword={}", keyword);

        List<Asset> results = assetRepository.searchByKeyword(keyword, 20);

        log.debug("심볼 검색 성공: keyword={}, count={}", keyword, results.size());
        return results;
    }

    @Override
    @Transactional
    public Asset createAsset(String symbol, String name, String country,
                              String currency, String assetType,
                              boolean collectData, LocalDate fromDate,
                              LocalDate toDate, boolean includeDividends) {
        log.info("종목 추가 요청: symbol={}, collectData={}", symbol, collectData);

        String finalSymbol = symbol.toUpperCase();

        if (assetRepository.findById(finalSymbol).isPresent()) {
            throw new IllegalArgumentException("Already exists: " + finalSymbol);
        }

        // 정보가 부족하면 SymbolSource에서 조회
        if (name == null || country == null || currency == null || assetType == null) {
            if (assetInfoSource == null) {
                throw new IllegalArgumentException("AssetInfoSource not available");
            }

            Asset fetched = assetInfoSource.getAsset(finalSymbol);
            if (fetched == null) {
                throw new IllegalArgumentException("Symbol not found: " + finalSymbol);
            }

            name = name != null ? name : fetched.getName();
            country = country != null ? country : fetched.getCountry();
            currency = currency != null ? currency : fetched.getCurrency();
            assetType = assetType != null ? assetType : fetched.getAssetType();
        }

        Asset asset = Asset.builder()
                .symbol(finalSymbol)
                .name(name)
                .country(country)
                .currency(currency)
                .assetType(assetType)
                .build();

        Asset saved = assetRepository.save(asset);
        log.info("종목 추가 성공: symbol={}", finalSymbol);

        // Kafka 이벤트 발행 (비동기 데이터 수집)
        assetEventProducer.publishAssetCreated(
                saved, collectData, fromDate, toDate, includeDividends);

        return saved;
    }

    @Override
    @Transactional(readOnly = true)
    public Optional<Asset> getAsset(String symbol) {
        log.debug("종목 조회 요청: symbol={}", symbol);

        Optional<Asset> asset = assetRepository.findById(symbol.toUpperCase());
        if (asset.isPresent()) {
            log.debug("종목 조회 성공: symbol={}", symbol);
        } else {
            log.warn("종목 조회 실패: symbol={} (존재하지 않음)", symbol);
        }

        return asset;
    }

    @Override
    @Transactional(readOnly = true)
    public List<Asset> getAllAssets() {
        log.debug("전체 종목 조회 요청");

        List<Asset> assets = assetRepository.findAll();
        log.debug("전체 종목 조회 성공: count={}", assets.size());

        return assets;
    }

    @Override
    @Transactional(readOnly = true)
    public Page<AssetSummaryDto> getAllAssetSummaries(Pageable pageable) {
        log.debug("전체 종목 요약 정보 조회 요청 (페이지: {}, 크기: {})",
                pageable.getPageNumber(), pageable.getPageSize());

        // 최신가/최신날짜가 asset에 비정규화되어 있어 candle 조회 없이 DB에서 직접 정렬+페이징
        // (candle 2967만 행을 매 요청마다 조회하던 병목 제거)
        Pageable dbPageable = remapSort(pageable);
        Page<Asset> page = assetRepository.findByActiveTrue(dbPageable);

        List<AssetSummaryDto> content = page.getContent().stream()
                .map(AssetSummaryDto::from)
                .collect(Collectors.toList());

        log.debug("활성 종목 요약 정보 조회 완료: page={}, totalElements={}",
                pageable.getPageNumber(), page.getTotalElements());
        return new PageImpl<>(content, pageable, page.getTotalElements());
    }

    /**
     * 프론트엔드 정렬 필드명을 Asset 엔티티 필드명으로 매핑
     * currentPrice → latestClose, latestDataDate → latestDate
     */
    private Pageable remapSort(Pageable pageable) {
        Sort sort = pageable.getSort();
        if (sort.isUnsorted()) {
            return pageable;
        }
        Sort mapped = Sort.by(sort.stream()
                .map(order -> {
                    String prop = switch (order.getProperty()) {
                        case "currentPrice" -> "latestClose";
                        case "latestDataDate" -> "latestDate";
                        default -> order.getProperty();
                    };
                    return new Sort.Order(order.getDirection(), prop).nullsLast();
                })
                .collect(Collectors.toList()));
        return org.springframework.data.domain.PageRequest.of(
                pageable.getPageNumber(), pageable.getPageSize(), mapped);
    }

    @Override
    @Transactional
    public void updateAssetPrice(String symbol) {
        log.info("종목 가격 업데이트 요청: symbol={}", symbol);

        String upperSymbol = symbol.toUpperCase();

        // Asset 존재 확인
        Asset asset = assetRepository.findById(upperSymbol)
                .orElseThrow(() -> new IllegalArgumentException("Not found: " + upperSymbol));

        if (candleSource == null) {
            log.warn("CandleSource가 활성화되지 않음");
            throw new IllegalStateException("CandleSource not available");
        }

        // 최근 1개월치 캔들 데이터 수집
        LocalDate to = LocalDate.now();
        LocalDate from = to.minusMonths(1);

        List<Candle> candles = candleSource.fetchDailyAdjusted(upperSymbol, from, to);

        if (candles.isEmpty()) {
            log.warn("캔들 데이터가 없음: symbol={}", upperSymbol);
            throw new IllegalStateException("No candle data available for: " + upperSymbol);
        }

        // 기존 데이터와 중복 확인하여 저장
        int savedCount = 0;
        for (Candle candle : candles) {
            if (!candleRepository.existsBySymbolAndDate(candle.getSymbol(), candle.getDate())) {
                candleRepository.save(candle);
                savedCount++;
            }
        }

        log.info("종목 가격 업데이트 완료: symbol={}, 저장된 캔들 개수={}", upperSymbol, savedCount);
    }

    @Override
    @Transactional
    public void updateAssetMarketCap(String symbol) {
        log.info("종목 시가총액 업데이트 요청: symbol={}", symbol);

        String upperSymbol = symbol.toUpperCase();

        // Asset 존재 확인
        assetRepository.findById(upperSymbol)
                .orElseThrow(() -> new IllegalArgumentException("Not found: " + upperSymbol));

        if (marketCapSource == null) {
            log.warn("시가총액 소스가 활성화되지 않음");
            throw new IllegalStateException("Market cap source not available");
        }

        // 활성 provider(Yahoo/AlphaVantage)에 위임
        boolean ok = marketCapSource.updateMarketCap(upperSymbol);
        if (!ok) {
            throw new IllegalStateException("Market cap not available for: " + upperSymbol);
        }
    }

    @Override
    @Transactional
    public int updateAllMarketCaps() {
        log.info("전체 종목 시가총액 일괄 업데이트 요청");

        if (marketCapSource == null) {
            throw new IllegalStateException("Market cap source not available");
        }
        return marketCapSource.updateAllMarketCaps(assetRepository.findByActiveTrue());
    }

    @Override
    @Transactional
    public void deleteAsset(String symbol) {
        log.info("종목 삭제 요청 (soft delete): symbol={}", symbol);

        String upperSymbol = symbol.toUpperCase();

        Asset asset = assetRepository.findById(upperSymbol)
                .orElseThrow(() -> new IllegalArgumentException("Not found: " + upperSymbol));

        // Soft delete: active를 false로 설정하고 delistedDate 기록
        asset.setActive(false);
        asset.setDelistedDate(LocalDate.now());
        assetRepository.save(asset);

        log.info("종목 삭제 성공 (soft delete): symbol={}, delistedDate={}",
                upperSymbol, asset.getDelistedDate());
    }

}
