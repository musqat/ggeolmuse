package com.muscat.marketdata.config;

import com.muscat.marketdata.domain.entity.Asset;
import com.muscat.marketdata.domain.repository.AssetRepository;
import com.muscat.marketdata.infra.kafka.AssetEventProducer;
import com.muscat.marketdata.datasource.yf.client.NasdaqScreenerClient;
import com.muscat.marketdata.datasource.yf.client.NasdaqScreenerParser;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.CommandLineRunner;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.core.io.ClassPathResource;
import org.springframework.stereotype.Component;

import java.io.BufferedReader;
import java.io.InputStreamReader;
import java.nio.charset.StandardCharsets;
import java.time.LocalDate;
import java.util.ArrayList;
import java.util.List;

/**
 * NASDAQ API 또는 CSV 파일에서 종목 정보를 읽어서 Asset 테이블에 초기화
 *
 * 1. NASDAQ API 호출 (우선)
 * 2. CSV 파일 (fallback)
 *
 * application.yml에서 활성화:
 *   marketdata.symbol-loader.enabled: true
 */
@Slf4j
@Component
@RequiredArgsConstructor
@ConditionalOnProperty(name = "marketdata.symbol-loader.enabled", havingValue = "true", matchIfMissing = false)
public class SymbolFileLoader implements CommandLineRunner {

    private final AssetRepository assetRepository;
    private final AssetEventProducer assetEventProducer;
    private final NasdaqScreenerClient nasdaqClient;
    private final NasdaqScreenerParser nasdaqParser;

    @Value("${marketdata.symbol-loader.use-api:true}")
    private boolean useApi;

    @Value("${marketdata.symbol-loader.market-cap-filter:mega,large}")
    private String marketCapFilter;

    @Value("${marketdata.symbol-loader.collect-data:false}")
    private boolean collectDataEnabled;

    @Value("${marketdata.symbol-loader.lookback-days:365}")
    private int lookbackDays;

    @Value("${marketdata.symbol-loader.include-dividends:true}")
    private boolean includeDividends;

    @Value("${marketdata.symbol-loader.max-symbols:0}")
    private int maxSymbols;  // 0 = 무제한, N = 시가총액 상위 N개

    @Override
    public void run(String... args) throws Exception {
        log.info("=== 종목 로더 시작 (API 모드: {}) ===", useApi);

        // 기존 데이터 확인
        long existingCount = assetRepository.count();
        if (existingCount > 0) {
            log.info("이미 {}개의 종목이 등록되어 있습니다. 스킵합니다.", existingCount);
            return;
        }

        List<Asset> allAssets = new ArrayList<>();

        if (useApi) {
            // API로 종목 로드 (우선)
            allAssets = loadFromNasdaqApi();
        }

        // API 실패 시 CSV fallback
        if (allAssets.isEmpty()) {
            log.warn("API 로드 실패, CSV 파일로 fallback");
            allAssets = loadFromCsvFiles();
        }

        // 3. DB 저장
        if (!allAssets.isEmpty()) {
            assetRepository.saveAll(allAssets);
            log.info("=== 총 {}개 종목 DB 저장 완료 ===", allAssets.size());

            // 4. 데이터 수집 이벤트 발행 (선택적)
            if (collectDataEnabled) {
                log.info("데이터 수집 시작: 총 {}개 종목, lookbackDays={}, includeDividends={}",
                        allAssets.size(), lookbackDays, includeDividends);

                LocalDate toDate = LocalDate.now();
                LocalDate fromDate = toDate.minusDays(lookbackDays);

                for (Asset asset : allAssets) {
                    try {
                        assetEventProducer.publishAssetCreated(
                                asset,
                                true,  // collectData
                                fromDate,
                                toDate,
                                includeDividends
                        );
                        log.debug("데이터 수집 이벤트 발행: symbol={}", asset.getSymbol());
                    } catch (Exception e) {
                        log.error("데이터 수집 이벤트 발행 실패: symbol={}, error={}",
                                asset.getSymbol(), e.getMessage());
                    }
                }

                log.info("=== 데이터 수집 이벤트 발행 완료: {}개 종목 ===", allAssets.size());
            } else {
                log.info("데이터 수집 비활성화됨 (marketdata.symbol-loader.collect-data=false)");
            }
        } else {
            log.warn("로드된 종목이 없습니다!");
        }
    }

    /**
     * NASDAQ API에서 종목 로드
     */
    private List<Asset> loadFromNasdaqApi() {
        List<Asset> allAssets = new ArrayList<>();

        try {
            log.info("NASDAQ API 종목 로드 시작: 시가총액 필터={}", marketCapFilter);

            // 1. NYSE 종목
            log.info("NASDAQ API에서 NYSE 종목 조회 시작...");
            String nyseJson = nasdaqClient.getAllStocks("nyse", marketCapFilter);
            List<Asset> nyseStocks = nasdaqParser.parseStocks(nyseJson);
            allAssets.addAll(nyseStocks);
            log.info("NYSE 종목 로드 완료: {}개", nyseStocks.size());

            // Rate limit 방지
            Thread.sleep(1000);

            // 2. NASDAQ 종목
            log.info("NASDAQ API에서 NASDAQ 종목 조회 시작...");
            String nasdaqJson = nasdaqClient.getAllStocks("nasdaq", marketCapFilter);
            List<Asset> nasdaqStocks = nasdaqParser.parseStocks(nasdaqJson);
            allAssets.addAll(nasdaqStocks);
            log.info("NASDAQ 종목 로드 완료: {}개", nasdaqStocks.size());

            log.info("=== NASDAQ API에서 총 {}개 종목 로드 완료 (필터: {}) ===",
                     allAssets.size(), marketCapFilter);

        } catch (Exception e) {
            log.error("NASDAQ API 로드 실패", e);
            return new ArrayList<>();
        }

        return allAssets;
    }

    /**
     * CSV 파일에서 종목 로드 (Fallback)
     */
    private List<Asset> loadFromCsvFiles() {
        List<Asset> allAssets = new ArrayList<>();

        // 1. NYSE Stocks 로드
        List<Asset> nyseStocks = loadNasdaqCsv("symbols/nyse_stocks.csv");
        allAssets.addAll(nyseStocks);
        log.info("NYSE 주식 종목 로드 완료: {}개 (CSV)", nyseStocks.size());

        // 2. NASDAQ Stocks 로드
        List<Asset> nasdaqStocks = loadNasdaqCsv("symbols/nasdaq_stocks.csv");
        allAssets.addAll(nasdaqStocks);
        log.info("NASDAQ 주식 종목 로드 완료: {}개 (CSV)", nasdaqStocks.size());

        log.info("=== CSV 파일에서 총 {}개 종목 로드 완료 ===", allAssets.size());
        return allAssets;
    }

    /**
     * NASDAQ CSV 형식 파일 로드
     * 형식: Symbol,Name,Last Sale,Net Change,% Change,Market Cap,Country,IPO Year,Volume,Sector,Industry
     */
    private List<Asset> loadNasdaqCsv(String filePath) {
        List<Asset> assets = new ArrayList<>();

        try {
            ClassPathResource resource = new ClassPathResource(filePath);

            try (BufferedReader reader = new BufferedReader(
                    new InputStreamReader(resource.getInputStream(), StandardCharsets.UTF_8))) {

                String line;
                boolean isFirstLine = true;

                while ((line = reader.readLine()) != null) {
                    // 헤더 라인 스킵
                    if (isFirstLine) {
                        isFirstLine = false;
                        continue;
                    }

                    // 빈 줄 스킵
                    line = line.trim();
                    if (line.isEmpty()) {
                        continue;
                    }

                    // CSV 파싱
                    String[] parts = line.split(",");
                    if (parts.length < 11) {
                        log.warn("잘못된 CSV 라인 (컬럼 수 부족): {}", line);
                        continue;
                    }

                    try {
                        String symbol = parts[0].trim();
                        String name = parts[1].trim();
                        String marketCapStr = parts[5].trim();
                        String country = parts[6].trim();
                        String sector = parts[9].trim();

                        // Market Cap 파싱 (예: "3899609280300.00" -> Long)
                        Long marketCap = null;
                        if (!marketCapStr.isEmpty()) {
                            try {
                                marketCap = Long.parseLong(marketCapStr.split("\\.")[0]);
                            } catch (NumberFormatException e) {
                                log.debug("시가총액 파싱 실패: symbol={}, marketCap={}", symbol, marketCapStr);
                            }
                        }

                        // Country가 비어있거나 전체 이름이면 ISO code로 변경
                        if (country.isEmpty() || "United States".equals(country)) {
                            country = "US"; // ISO 2-letter code
                        }

                        // Asset Type 판별 (EQUITY vs ETF)
                        String assetType = determineAssetType(name);

                        // Asset 생성
                        Asset asset = Asset.builder()
                                .symbol(symbol)
                                .name(name)
                                .country(country)
                                .currency("USD")
                                .assetType(assetType)
                                .marketCap(marketCap)
                                .build();

                        assets.add(asset);
                        log.debug("종목 로드: {} - {} (Asset Type: {}, Market Cap: {})", symbol, name, assetType, marketCap);

                    } catch (Exception e) {
                        log.warn("종목 파싱 실패: line={}, error={}", line, e.getMessage());
                    }
                }
            }

        } catch (Exception e) {
            log.error("종목 파일 로드 실패: {}", filePath, e);
        }

        return assets;
    }

    /**
     * 종목명을 분석하여 Asset Type 판별 (EQUITY vs ETF)
     */
    private String determineAssetType(String name) {
        if (name == null) {
            return "EQUITY";
        }

        String nameLower = name.toLowerCase();

        // ETF 관련 키워드 체크
        if (nameLower.contains(" etf") ||
            nameLower.contains("exchange traded fund") ||
            nameLower.contains(" trust") ||
            nameLower.contains(" fund") ||
            nameLower.contains("index fund") ||
            nameLower.endsWith(" etf")) {
            return "ETF";
        }

        // 기본값: EQUITY
        return "EQUITY";
    }
}
