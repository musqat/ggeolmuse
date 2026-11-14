package com.muscat.marketdata.datasource.fixed;

import com.muscat.marketdata.datasource.common.MarketDataProvider.AssetInfoSource;
import com.muscat.marketdata.datasource.common.MarketDataProvider.CandleSource;
import com.muscat.marketdata.datasource.common.MarketDataProvider.DividendSource;
import com.muscat.marketdata.datasource.common.MarketDataProvider.FxSource;
import com.muscat.marketdata.datasource.common.MarketDataProvider.ListingSource;
import com.muscat.marketdata.datasource.common.MarketDataProvider.MarketCapSource;
import com.muscat.marketdata.domain.dto.DividendDto;
import com.muscat.marketdata.domain.entity.Asset;
import com.muscat.marketdata.domain.entity.Candle;
import java.io.BufferedReader;
import java.io.IOException;
import java.io.InputStreamReader;
import java.io.UncheckedIOException;
import java.math.BigDecimal;
import java.nio.charset.StandardCharsets;
import java.time.LocalDate;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Map.Entry;
import java.util.NavigableMap;
import java.util.Optional;
import java.util.TreeMap;
import lombok.extern.slf4j.Slf4j;
import org.springframework.context.annotation.Primary;
import org.springframework.context.annotation.Profile;
import org.springframework.core.io.ClassPathResource;
import org.springframework.stereotype.Component;

// fixed-quotes 프로필에서 종목 목록 · 일봉 · 배당 · 환율 · 시가총액을 클래스패스 고정 데이터로 주는 공급원(E2E 용, prod 와 같이 켜면 등록 안 함)
@Slf4j
@Primary
@Component
@Profile(FixedQuoteSource.PROFILE + " & !prod")
public class FixedQuoteSource implements ListingSource, CandleSource, DividendSource, FxSource,
    AssetInfoSource, MarketCapSource {

  public static final String PROFILE = "fixed-quotes";

  private static final String DIR = "fixed-quotes/";
  private static final String CURRENCY = "USD";
  private static final String ORIGIN = "fixed-quotes";

  private record Listed(String symbol, String name, String assetType, long marketCap) {
  }

  private record Bar(LocalDate date, BigDecimal open, BigDecimal high, BigDecimal low, BigDecimal close,
      long volume) {
  }

  private record Payout(LocalDate exDate, BigDecimal amount) {
  }

  private final Map<String, Listed> listing = new LinkedHashMap<>();
  private final Map<String, NavigableMap<LocalDate, Bar>> bars = new LinkedHashMap<>();
  private final Map<String, NavigableMap<LocalDate, Payout>> payouts = new LinkedHashMap<>();
  private final NavigableMap<LocalDate, BigDecimal> rates = new TreeMap<>();

  public FixedQuoteSource() {
    for (String[] row : read("symbols.csv")) {
      listing.put(row[0], new Listed(row[0], row[1], row[2], Long.parseLong(row[3])));
    }
    for (String[] row : read("candles.csv")) {
      Bar bar = new Bar(LocalDate.parse(row[1]), new BigDecimal(row[2]), new BigDecimal(row[3]),
          new BigDecimal(row[4]), new BigDecimal(row[5]), Long.parseLong(row[6]));
      bars.computeIfAbsent(row[0], symbol -> new TreeMap<>()).put(bar.date(), bar);
    }
    for (String[] row : read("dividends.csv")) {
      Payout payout = new Payout(LocalDate.parse(row[1]), new BigDecimal(row[2]));
      payouts.computeIfAbsent(row[0], symbol -> new TreeMap<>()).put(payout.exDate(), payout);
    }
    for (String[] row : read("fx.csv")) {
      rates.put(LocalDate.parse(row[0]), new BigDecimal(row[1]));
    }
    log.info("[고정시세] 종목 {}개, 일봉 {}건, 환율 {}건", listing.size(),
        bars.values().stream().mapToInt(Map::size).sum(), rates.size());
  }

  // 머리줄을 뺀 CSV 줄(종목 이름에 쉼표 없음)
  private static List<String[]> read(String file) {
    List<String[]> rows = new ArrayList<>();
    try (BufferedReader reader = new BufferedReader(new InputStreamReader(
        new ClassPathResource(DIR + file).getInputStream(), StandardCharsets.UTF_8))) {
      reader.lines().skip(1).filter(line -> !line.isBlank()).map(line -> line.split(",")).forEach(rows::add);
    } catch (IOException e) {
      throw new UncheckedIOException("고정 시세 파일을 읽지 못했다: " + DIR + file, e);
    }
    return rows;
  }

  @Override
  public List<Asset> fetch() {
    return listing.values().stream().map(FixedQuoteSource::toAsset).toList();
  }

  @Override
  public boolean isCapped() {
    return false;
  }

  @Override
  public List<Candle> fetchDailyAdjusted(String symbol, LocalDate from, LocalDate to) {
    String key = symbol.toUpperCase(Locale.ROOT);
    return bars.getOrDefault(key, new TreeMap<>()).subMap(from, true, to, true).values().stream()
        .map(bar -> Candle.builder()
            .symbol(key)
            .date(bar.date())
            .currency(CURRENCY)
            .open(bar.open())
            .high(bar.high())
            .low(bar.low())
            .close(bar.close())
            .adjustedClose(bar.close())
            .volume(bar.volume())
            .build())
        .toList();
  }

  @Override
  public List<DividendDto> fetchDividends(String symbol, LocalDate from, LocalDate to) {
    String key = symbol.toUpperCase(Locale.ROOT);
    return payouts.getOrDefault(key, new TreeMap<>()).subMap(from, true, to, true).values().stream()
        .map(payout -> DividendDto.builder()
            .symbol(key)
            .exDate(payout.exDate())
            .amount(payout.amount())
            .currency(CURRENCY)
            .source(ORIGIN)
            .build())
        .toList();
  }

  // 요청한 날 또는 앞 날 환율, 고정 기간 앞이면 첫날 환율
  @Override
  public Optional<BigDecimal> fetchFx(LocalDate date) {
    Entry<LocalDate, BigDecimal> rate = Optional.ofNullable(rates.floorEntry(date)).orElse(rates.firstEntry());
    return Optional.ofNullable(rate).map(Entry::getValue);
  }

  @Override
  public Asset getAsset(String symbol) {
    Listed listed = listing.get(symbol.toUpperCase(Locale.ROOT));
    return listed == null ? null : toAsset(listed);
  }

  // 시가총액은 목록 값 그대로라 바꾸는 종목 없음
  @Override
  public int updateAllMarketCaps(List<Asset> assets) {
    return 0;
  }

  @Override
  public boolean updateMarketCap(String symbol) {
    return false;
  }

  private static Asset toAsset(Listed listed) {
    return Asset.builder()
        .symbol(listed.symbol())
        .name(listed.name())
        .sourceName(listed.name())
        .country("US")
        .currency(CURRENCY)
        .assetType(listed.assetType())
        .marketCap(listed.marketCap())
        .build();
  }
}
