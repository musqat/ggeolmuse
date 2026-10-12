package com.muscat.marketdata.api.controller;

import static org.mockito.BDDMockito.given;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import com.muscat.marketdata.domain.entity.FxRate;
import com.muscat.marketdata.domain.repository.FxRateRepository;
import com.muscat.marketdata.domain.service.CandleService;
import com.muscat.marketdata.domain.service.DividendService;
import com.muscat.marketdata.domain.service.FxRateService;
import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.Optional;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.setup.MockMvcBuilders;

@ExtendWith(MockitoExtension.class)
@DisplayName("내부 환율 범위 API")
class InternalMarketControllerFxRangeTest {

  @Mock
  private CandleService candleService;

  @Mock
  private DividendService dividendService;

  @Mock
  private FxRateService fxRateService;

  @Mock
  private FxRateRepository fxRateRepository;

  @InjectMocks
  private InternalMarketController controller;

  private MockMvc mockMvc;

  @BeforeEach
  void setUp() {
    mockMvc = MockMvcBuilders.standaloneSetup(controller).build();
  }

  @Test
  @DisplayName("최저 · 최고 환율과 날짜를 user-service 가 읽는 모양으로 준다")
  void returnsRange() throws Exception {
    given(fxRateRepository.findFirstByOrderByRateAsc()).willReturn(Optional.of(
      FxRate.builder().date(LocalDate.of(2018, 4, 3)).rate(new BigDecimal("1053.37")).build()));
    given(fxRateRepository.findFirstByOrderByRateDesc()).willReturn(Optional.of(
      FxRate.builder().date(LocalDate.of(2026, 7, 2)).rate(new BigDecimal("1554.40")).build()));

    mockMvc.perform(get("/api/internal/market/fx/range"))
      .andExpect(status().isOk())
      .andExpect(jsonPath("$.min").value(1053.37))
      .andExpect(jsonPath("$.minDate").value("2018-04-03"))
      .andExpect(jsonPath("$.max").value(1554.40))
      .andExpect(jsonPath("$.maxDate").value("2026-07-02"));
  }

  @Test
  @DisplayName("환율 데이터가 없으면 404")
  void notFoundWhenEmpty() throws Exception {
    given(fxRateRepository.findFirstByOrderByRateAsc()).willReturn(Optional.empty());
    given(fxRateRepository.findFirstByOrderByRateDesc()).willReturn(Optional.empty());

    mockMvc.perform(get("/api/internal/market/fx/range"))
      .andExpect(status().isNotFound());
  }
}
