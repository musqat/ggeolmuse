package com.muscat.marketdata.api.controller;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.BDDMockito.given;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import com.muscat.marketdata.common.exception.GlobalExceptionHandler;
import com.muscat.marketdata.config.security.SecurityConfig;
import com.muscat.marketdata.datasource.common.CandleBatchService;
import com.muscat.marketdata.domain.dto.BatchResult;
import com.muscat.marketdata.domain.service.CandleUpdateService;
import java.time.LocalDate;
import java.time.ZoneId;
import java.util.List;
import java.util.Map;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.WebMvcTest;
import org.springframework.http.HttpHeaders;
import org.springframework.http.ResponseEntity;
import org.springframework.security.oauth2.jwt.Jwt;
import org.springframework.security.oauth2.jwt.JwtDecoder;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.context.ContextConfiguration;
import org.springframework.test.context.TestPropertySource;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.test.util.ReflectionTestUtils;
import org.springframework.test.web.servlet.MockMvc;

// provider=alphavantage 일 때만 뜨는 일괄 수집 컨트롤러. 관리자 경로 아래라 admin 만 닿음
@DisplayName("일괄 수집 컨트롤러")
class DataCollectionControllerTest {

  @Nested
  @DisplayName("동작")
  class BehaviorTests {

    private final CandleBatchService batchService = mock(CandleBatchService.class);
    private final CandleUpdateService updateService = mock(CandleUpdateService.class);
    private final DataCollectionController controller = new DataCollectionController(batchService, updateService);
    private final LocalDate today = LocalDate.now(ZoneId.of("America/New_York"));

    {
      ReflectionTestUtils.setField(controller, "defaultLookbackDaysAll", 365);
      ReflectionTestUtils.setField(controller, "defaultLookbackDaysSingle", 90);
    }

    @Test
    @DisplayName("전체 수집은 기간을 주지 않으면 뉴욕 오늘부터 365일 전까지 받고 결과 요약을 준다")
    void collectAllDefaults() {
      given(batchService.collectAll(today.minusDays(365), today, true))
        .willReturn(new BatchResult(10, 9, 1, 2500, today.minusDays(365), today));

      ResponseEntity<Map<String, Object>> response = controller.collectAllSymbols(null, null, true);

      assertThat(response.getBody()).containsEntry("summary",
        Map.of("totalSymbols", 10, "successCount", 9, "failureCount", 1, "totalSavedRecords", 2500));
    }

    @Test
    @DisplayName("종목 하나는 기본 90일이고, 배당 포함 여부로 저장 방식이 갈리며 종목 코드를 대문자로 돌려준다")
    void collectSingle() {
      LocalDate from = LocalDate.of(2024, 1, 1);
      LocalDate to = LocalDate.of(2024, 3, 31);
      given(updateService.saveBoth("aapl", today.minusDays(90), today)).willReturn(12);
      given(updateService.saveCandles("aapl", from, to)).willReturn(7);

      ResponseEntity<Map<String, Object>> both = controller.collectSingleSymbol("aapl", null, null, true);
      ResponseEntity<Map<String, Object>> candles = controller.collectSingleSymbol("aapl", from, to, false);

      assertThat(both.getBody()).containsEntry("symbol", "AAPL")
        .containsEntry("result", Map.of("savedRecords", 12, "includeDividends", true));
      assertThat(candles.getBody()).containsEntry("result", Map.of("savedRecords", 7, "includeDividends", false));
    }

    @Test
    @DisplayName("상태는 종목 수와 기본 조회 기간을 준다")
    void status() {
      given(batchService.loadSymbols()).willReturn(List.of("AAPL", "MSFT"));

      assertThat(controller.getBatchStatus().getBody()).containsEntry("symbolsCount", 2)
        .containsEntry("defaultLookbackDays", Map.of("allAssets", 365, "singleSymbol", 90));
    }

    @Test
    @DisplayName("수집 · 조회가 실패하면 같은 예외를 올린다")
    void failures() {
      given(batchService.collectAll(org.mockito.ArgumentMatchers.any(), org.mockito.ArgumentMatchers.any(), org.mockito.ArgumentMatchers.anyBoolean()))
        .willThrow(new IllegalStateException("limit"));
      given(updateService.saveCandles(org.mockito.ArgumentMatchers.any(), org.mockito.ArgumentMatchers.any(), org.mockito.ArgumentMatchers.any()))
        .willThrow(new IllegalStateException("limit"));
      given(batchService.loadSymbols()).willThrow(new IllegalStateException("db down"));

      assertThatThrownBy(() -> controller.collectAllSymbols(null, null, false)).hasMessage("limit");
      assertThatThrownBy(() -> controller.collectSingleSymbol("AAPL", null, null, false)).hasMessage("limit");
      assertThatThrownBy(controller::getBatchStatus).hasMessage("db down");
    }
  }

  @Nested
  @WebMvcTest(DataCollectionController.class)
  @ContextConfiguration(classes = {DataCollectionController.class, GlobalExceptionHandler.class, SecurityConfig.class})
  @TestPropertySource(properties = "marketdata.provider=alphavantage")
  @ActiveProfiles("test")
  @DisplayName("보안")
  class SecurityTests {

    @Autowired
    private MockMvc mockMvc;

    @MockitoBean
    private CandleBatchService batchService;

    @MockitoBean
    private CandleUpdateService updateService;

    @MockitoBean
    private JwtDecoder jwtDecoder;

    @Test
    @DisplayName("일괄 수집은 토큰이 없으면 401, admin 이 아니면 403 이고 admin 이면 서비스까지 간다")
    void adminOnly() throws Exception {
      given(jwtDecoder.decode("admin-token")).willReturn(jwt("admin-token", "admin"));
      given(jwtDecoder.decode("user-token")).willReturn(jwt("user-token", "user"));
      given(batchService.loadSymbols()).willReturn(List.of("AAPL"));

      mockMvc.perform(get("/api/admin/market/batch/status")).andExpect(status().isUnauthorized());
      mockMvc.perform(post("/api/admin/market/batch/candles").header(HttpHeaders.AUTHORIZATION, "Bearer user-token"))
        .andExpect(status().isForbidden());
      verify(batchService, never()).loadSymbols();

      mockMvc.perform(get("/api/admin/market/batch/status").header(HttpHeaders.AUTHORIZATION, "Bearer admin-token"))
        .andExpect(status().isOk());
      verify(batchService).loadSymbols();
    }

    private static Jwt jwt(String token, String role) {
      return Jwt.withTokenValue(token).header("alg", "none")
        .subject("user-1").claim("realm_access", Map.of("roles", List.of(role))).build();
    }
  }
}
