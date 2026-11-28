package com.muscat.trade.domain.service;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.anyLong;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.BDDMockito.given;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;

import com.muscat.commonlib.dto.OHLCPriceDto;
import com.muscat.jpatest.TradeJpaTestConfig;
import com.muscat.trade.common.enums.type.TradeStatus;
import com.muscat.trade.common.enums.type.TradeType;
import com.muscat.trade.common.logging.TradeLogger;
import com.muscat.trade.config.TradeProperties;
import com.muscat.trade.domain.entity.Holdings;
import com.muscat.trade.domain.entity.Trade;
import com.muscat.trade.domain.repository.HoldingsRepository;
import com.muscat.trade.domain.repository.StockSplitRepository;
import com.muscat.trade.domain.repository.TradeRepository;
import com.muscat.trade.domain.service.StockSplitService.Split;
import com.muscat.trade.infra.client.MarketServiceClientWrapper;
import com.muscat.trade.infra.kafka.DividendReconcileProducer;
import io.micrometer.core.instrument.MeterRegistry;
import io.micrometer.core.instrument.simple.SimpleMeterRegistry;
import java.math.BigDecimal;
import java.time.Clock;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.List;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.data.jpa.test.autoconfigure.DataJpaTest;
import org.springframework.boot.jdbc.test.autoconfigure.AutoConfigureTestDatabase;
import org.springframework.boot.test.context.TestConfiguration;
import org.springframework.boot.testcontainers.service.connection.ServiceConnection;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Import;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.context.ContextConfiguration;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.transaction.annotation.Propagation;
import org.springframework.transaction.annotation.Transactional;
import org.testcontainers.containers.PostgreSQLContainer;
import org.testcontainers.junit.jupiter.Container;
import org.testcontainers.junit.jupiter.Testcontainers;

// Flyway 로 만든 Postgres 스키마에서 분할 전 체결 · 보유를 분할 기준으로 맞춘 뒤 평가액 확인
@DataJpaTest(properties = {
  "spring.jpa.hibernate.ddl-auto=validate",
  "spring.jpa.database-platform=org.hibernate.dialect.PostgreSQLDialect"
})
@AutoConfigureTestDatabase(replace = AutoConfigureTestDatabase.Replace.NONE)
@ActiveProfiles("test")
@ContextConfiguration(classes = TradeJpaTestConfig.class)
@Import({StockSplitService.class, HoldingsRebuilder.class, StockSplitServiceIT.Beans.class})
@Transactional(propagation = Propagation.NOT_SUPPORTED)
@Testcontainers
@DisplayName("주식 분할 반영 통합 테스트 (TestContainers)")
class StockSplitServiceIT {

  @Container
  @ServiceConnection
  static PostgreSQLContainer<?> postgres = new PostgreSQLContainer<>("postgres:15-alpine");

  private static final String SYMBOL = "NVDA";
  private static final LocalDate SPLIT_DATE = LocalDate.of(2024, 6, 10);
  private static final BigDecimal RATIO = new BigDecimal("4");
  // 분할 뒤 지금 종가
  private static final BigDecimal CLOSE_NOW = new BigDecimal("120.00");

  @TestConfiguration
  static class Beans {
    @Bean
    TradeProperties tradeProperties() {
      return new TradeProperties();
    }

    @Bean
    MeterRegistry meterRegistry() {
      return new SimpleMeterRegistry();
    }

    @Bean
    Clock clock() {
      return Clock.systemDefaultZone();
    }
  }

  @MockitoBean
  private TradeLogger tradeLogger;

  @MockitoBean
  private MarketServiceClientWrapper marketServiceClientWrapper;

  @MockitoBean
  private DividendReconcileProducer dividendReconcileProducer;

  @Autowired
  private StockSplitService stockSplitService;

  @Autowired
  private TradeRepository tradeRepository;

  @Autowired
  private HoldingsRepository holdingsRepository;

  @Autowired
  private StockSplitRepository stockSplitRepository;

  @AfterEach
  void tearDown() {
    tradeRepository.deleteAllInBatch();
    holdingsRepository.deleteAllInBatch();
    stockSplitRepository.deleteAllInBatch();
  }

  private Trade completed(long accountId, TradeType type, String quantity, String price, LocalDate tradeDate) {
    BigDecimal amount = new BigDecimal(quantity).multiply(new BigDecimal(price));
    return tradeRepository.save(Trade.builder().userId("user-1").accountId(accountId).symbol(SYMBOL)
      .tradeType(type).quantity(new BigDecimal(quantity)).price(new BigDecimal(price)).totalAmount(amount)
      .tradeDate(tradeDate).executedAt(LocalDateTime.of(2024, 6, 3, 10, 0)).status(TradeStatus.COMPLETED).build());
  }

  private void holdings(long accountId, String quantity, String avgPrice, String invested) {
    holdingsRepository.save(Holdings.builder().userId("user-1").accountId(accountId).symbol(SYMBOL)
      .totalQuantity(new BigDecimal(quantity)).avgPurchasePrice(new BigDecimal(avgPrice))
      .totalInvestedAmount(new BigDecimal(invested)).build());
  }

  private Holdings holdingsOf(long accountId) {
    return holdingsRepository.findByUserIdAndAccountIdAndSymbol("user-1", accountId, SYMBOL).orElseThrow();
  }

  private static OHLCPriceDto close(LocalDate date, String close) {
    BigDecimal price = new BigDecimal(close);
    return new OHLCPriceDto(SYMBOL, date, price, price, price, price, price, 1L, "USD", true);
  }

  @Test
  @DisplayName("분할 전에 산 주식의 평가액이 분할 뒤 종가로도 같고, 같은 분할을 두 번 받아도 한 번만 반영")
  void apply_KeepsValueAcrossSplit() {
    // 분할 전 체결가(체결일 분할 기준 종가의 4배)의 10주 매수 · 2주 매도, 분할 뒤 체결가로 넣은 과거 날짜 매수, 분할 뒤 매수
    Trade buy = completed(30L, TradeType.BUY, "10", "400.00", LocalDate.of(2024, 6, 3));
    Trade sell = completed(30L, TradeType.SELL, "2", "404.00", LocalDate.of(2024, 6, 5));
    completed(30L, TradeType.BUY, "5", "110.00", LocalDate.of(2024, 6, 11));
    Trade pastDate = completed(31L, TradeType.BUY, "8", "101.00", LocalDate.of(2024, 6, 4));
    holdings(30L, "13", "288.46", "3750.00");
    holdings(31L, "8", "101.00", "808.00");
    given(marketServiceClientWrapper.getOHLCPriceRange(SYMBOL, "2024-06-03", "2024-06-09")).willReturn(List.of(
      close(LocalDate.of(2024, 6, 3), "100.00"), close(LocalDate.of(2024, 6, 4), "100.50"),
      close(LocalDate.of(2024, 6, 5), "101.00")));

    assertThat(stockSplitService.apply(SYMBOL, List.of(new Split(SPLIT_DATE, RATIO))).adjusted()).isEqualTo(2);

    // 분할 전 보유 8주(분할 전 종가 480) + 분할 뒤 5주의 평가액 그대로
    BigDecimal before = new BigDecimal("8").multiply(CLOSE_NOW.multiply(RATIO))
      .add(new BigDecimal("5").multiply(CLOSE_NOW));
    assertThat(holdingsOf(30L).getTotalQuantity().multiply(CLOSE_NOW)).isEqualByComparingTo(before);
    assertThat(holdingsOf(30L).getTotalQuantity()).isEqualByComparingTo("37");
    assertThat(holdingsOf(30L).getTotalInvestedAmount()).isEqualByComparingTo("3750.00");
    assertThat(holdingsOf(31L).getTotalQuantity()).isEqualByComparingTo("8");

    Trade adjustedBuy = tradeRepository.findById(buy.getId()).orElseThrow();
    assertThat(adjustedBuy.getQuantity()).isEqualByComparingTo("40");
    assertThat(adjustedBuy.getPrice()).isEqualByComparingTo("100");
    assertThat(adjustedBuy.getPreSplitQuantity()).isEqualByComparingTo("10");
    assertThat(adjustedBuy.getPreSplitPrice()).isEqualByComparingTo("400");
    assertThat(adjustedBuy.getSplitRatio()).isEqualByComparingTo("4");
    assertThat(adjustedBuy.getSplitAppliedAt()).isNotNull();
    assertThat(tradeRepository.findById(sell.getId()).orElseThrow().getQuantity()).isEqualByComparingTo("8");
    assertThat(tradeRepository.findById(pastDate.getId()).orElseThrow().getSplitRatio()).isNull();

    verify(dividendReconcileProducer).request("user-1", 30L, SYMBOL);
    verify(dividendReconcileProducer, never()).request("user-1", 31L, SYMBOL);

    assertThat(stockSplitService.apply(SYMBOL, List.of(new Split(SPLIT_DATE, RATIO))).adjusted()).isZero();
    assertThat(holdingsOf(30L).getTotalQuantity()).isEqualByComparingTo("37");
    assertThat(tradeRepository.findById(buy.getId()).orElseThrow().getQuantity()).isEqualByComparingTo("40");
    assertThat(stockSplitRepository.findAll()).singleElement()
      .satisfies(split -> assertThat(split.getAdjustedTrades()).isEqualTo(2));
    verify(dividendReconcileProducer, times(1)).request(anyString(), anyLong(), anyString());
  }
}
