package com.muscat.marketdata.domain.service.impl;

import com.muscat.marketdata.datasource.alphavantage.provider.CandleSource;
import com.muscat.marketdata.datasource.alphavantage.provider.SymbolSource;
import com.muscat.marketdata.domain.dto.AssetSummaryDto;
import com.muscat.marketdata.domain.entity.Asset;
import com.muscat.marketdata.domain.entity.Candle;
import com.muscat.marketdata.domain.repository.AssetRepository;
import com.muscat.marketdata.domain.repository.CandleRepository;
import com.muscat.marketdata.domain.service.AssetService;
import com.muscat.marketdata.infra.kafka.AssetEventProducer;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;
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
    private final SymbolSource symbolSource;
    private final CandleSource candleSource;
    private final AssetEventProducer assetEventProducer;

    public AssetServiceImpl(
            AssetRepository assetRepository,
            CandleRepository candleRepository,
            @Autowired(required = false) SymbolSource symbolSource,
            @Autowired(required = false) CandleSource candleSource,
            AssetEventProducer assetEventProducer) {
        this.assetRepository = assetRepository;
        this.candleRepository = candleRepository;
        this.symbolSource = symbolSource;
        this.candleSource = candleSource;
        this.assetEventProducer = assetEventProducer;
    }

    @Override
    @Transactional(readOnly = true)
    public Optional<Asset> previewSymbol(String symbol) {
        log.debug("심볼 미리보기 요청: symbol={}", symbol);

        if (symbolSource == null) {
            log.warn("SymbolSource가 활성화되지 않음");
            return Optional.empty();
        }

        Asset asset = symbolSource.getAsset(symbol.toUpperCase());
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
            if (symbolSource == null) {
                throw new IllegalArgumentException("SymbolSource not available");
            }

            Asset fetched = symbolSource.getAsset(finalSymbol);
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

        // active=true인 종목만 조회 (Pageable을 수동으로 처리)
        List<Asset> allActiveAssets = assetRepository.findByActiveTrue();

        // 페이징 처리
        int start = (int) pageable.getOffset();
        int end = Math.min((start + pageable.getPageSize()), allActiveAssets.size());
        List<Asset> pagedAssets = allActiveAssets.subList(start, end);

        log.debug("활성 종목 조회 성공: page={}, totalElements={}, totalPages={}",
                pageable.getPageNumber(), allActiveAssets.size(),
                (allActiveAssets.size() + pageable.getPageSize() - 1) / pageable.getPageSize());

        // Asset을 AssetSummaryDto로 변환 (currentPrice, latestDataDate 계산)
        List<AssetSummaryDto> summaries = pagedAssets.stream()
                .map(asset -> {
                    // 최신 Candle 데이터 조회
                    return candleRepository.findFirstBySymbolOrderByDateDesc(asset.getSymbol())
                            .map(candle -> AssetSummaryDto.of(
                                    asset, candle.getClose(), candle.getDate()))
                            .orElse(AssetSummaryDto.from(asset));
                })
                .collect(Collectors.toList());

        log.debug("활성 종목 요약 정보 변환 완료: count={}", summaries.size());
        return new PageImpl<>(summaries, pageable, allActiveAssets.size());
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
        Asset asset = assetRepository.findById(upperSymbol)
                .orElseThrow(() -> new IllegalArgumentException("Not found: " + upperSymbol));

        if (symbolSource == null) {
            log.warn("SymbolSource가 활성화되지 않음");
            throw new IllegalStateException("SymbolSource not available");
        }

        // AlphaVantage OVERVIEW API로 최신 정보 조회
        Asset updatedInfo = symbolSource.getAsset(upperSymbol);

        if (updatedInfo == null || updatedInfo.getMarketCap() == null || updatedInfo.getMarketCap() == 0) {
            log.warn("시가총액 정보 없음: symbol={}", upperSymbol);
            throw new IllegalStateException("Market cap not available for: " + upperSymbol);
        }

        // 시가총액 업데이트
        asset.setMarketCap(updatedInfo.getMarketCap());
        assetRepository.save(asset);

        log.info("종목 시가총액 업데이트 완료: symbol={}, marketCap={}",
                upperSymbol, updatedInfo.getMarketCap());
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
