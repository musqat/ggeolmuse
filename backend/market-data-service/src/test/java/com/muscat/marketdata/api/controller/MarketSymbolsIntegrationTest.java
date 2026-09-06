package com.muscat.marketdata.api.controller;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import com.muscat.marketdata.datasource.alphavantage.scheduler.AlphaVantageScheduler;
import com.muscat.marketdata.domain.entity.Asset;
import com.muscat.marketdata.domain.repository.AssetRepository;
import com.muscat.marketdata.domain.service.AssetService;
import com.muscat.marketdata.infra.kafka.AssetEventProducer;
import java.util.List;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.test.mock.mockito.MockBean;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.web.servlet.MockMvc;

/**
 * 종목 목록은 자동완성용이라 티커만 준다. 필드가 하나라도 늘거나 상장폐지가 섞이면 여기서 걸린다.
 * 이 응답 모양이 바뀌었을 때 E2E 넷이 깨진 적이 있다.
 */
@SpringBootTest
@AutoConfigureMockMvc
@ActiveProfiles("test")
@DisplayName("/api/market/symbols 통합 테스트")
class MarketSymbolsIntegrationTest {

  @Autowired
  private MockMvc mockMvc;

  @Autowired
  private AssetRepository assetRepository;

  @Autowired
  private AssetService assetService;

  @MockBean
  private AssetEventProducer assetEventProducer;

  @MockBean
  private AlphaVantageScheduler alphaVantageScheduler;

  @BeforeEach
  void seed() {
    assetRepository.deleteAll();
    assetRepository.saveAll(List.of(
        asset("MSFT", true),
        asset("AAPL", true),
        asset("DEAD", false)));
  }

  @AfterEach
  void clean() {
    assetRepository.deleteAll();
  }

  private static Asset asset(String symbol, boolean active) {
    return Asset.builder()
        .symbol(symbol)
        .name(symbol + " Inc.")
        .country("US")
        .currency("USD")
        .assetType("EQUITY")
        .active(active)
        .build();
  }

  @Test
  @DisplayName("상장폐지는 빼고 티커를 오름차순으로 준다")
  void activeSymbolsOnlyAndSorted() {
    assertThat(assetService.getActiveSymbols()).containsExactly("AAPL", "MSFT");
  }

  @Test
  @DisplayName("응답은 symbol 하나만 담은 배열이다")
  void responseCarriesSymbolOnly() throws Exception {
    mockMvc.perform(get("/api/market/symbols"))
        .andExpect(status().isOk())
        .andExpect(jsonPath("$.length()").value(2))
        .andExpect(jsonPath("$[0].symbol").value("AAPL"))
        .andExpect(jsonPath("$[1].symbol").value("MSFT"))
        .andExpect(jsonPath("$[0].length()").value(1))
        .andExpect(jsonPath("$[*].name").doesNotExist())
        .andExpect(jsonPath("$[?(@.symbol == 'DEAD')]").isEmpty());
  }
}
