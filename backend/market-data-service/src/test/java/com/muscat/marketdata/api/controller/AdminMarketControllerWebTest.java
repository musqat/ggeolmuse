package com.muscat.marketdata.api.controller;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyBoolean;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.argThat;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.BDDMockito.given;
import static org.mockito.BDDMockito.willThrow;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.timeout;
import static org.mockito.Mockito.verify;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.delete;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.put;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import com.muscat.marketdata.common.enums.response.MarketDataResponse;
import com.muscat.marketdata.common.exceptions.GlobalExceptionHandler;
import com.muscat.marketdata.common.exceptions.MarketDataException;
import com.muscat.marketdata.config.security.SecurityConfig;
import com.muscat.marketdata.datasource.yf.collector.SymbolCollector;
import com.muscat.marketdata.domain.dto.AssetSummaryDto;
import com.muscat.marketdata.domain.entity.AdminJobRun;
import com.muscat.marketdata.domain.entity.Asset;
import com.muscat.marketdata.domain.repository.AdminJobRunRepository;
import com.muscat.marketdata.domain.repository.AssetRepository;
import com.muscat.marketdata.domain.repository.CandleRepository;
import com.muscat.marketdata.domain.service.AssetService;
import com.muscat.marketdata.domain.service.CandleRefreshAllPublisher;
import com.muscat.marketdata.domain.service.UnadjustedScanService;
import com.muscat.marketdata.infra.kafka.AssetEventProducer;
import java.time.Instant;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.time.ZoneId;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.WebMvcTest;
import org.springframework.data.domain.PageImpl;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Sort;
import org.springframework.http.HttpHeaders;
import org.springframework.http.MediaType;
import org.springframework.security.oauth2.jwt.Jwt;
import org.springframework.security.oauth2.jwt.JwtDecoder;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.context.ContextConfiguration;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.ResultActions;
import org.springframework.test.web.servlet.request.MockHttpServletRequestBuilder;

// 관리자 경로는 Keycloak realm 역할 admin 이 권한 admin 으로 바뀐 토큰만 들어온다. 실제 보안 설정과 변환기를 쓴다
@WebMvcTest(AdminMarketController.class)
@ContextConfiguration(classes = {AdminMarketController.class, GlobalExceptionHandler.class, SecurityConfig.class})
@ActiveProfiles("test")
@DisplayName("AdminMarketController 관리자 API")
class AdminMarketControllerWebTest {

  private static final String BASE = "/api/admin/market";

  @Autowired
  private MockMvc mockMvc;

  @MockitoBean
  private AssetService assetService;

  @MockitoBean
  private AssetRepository assetRepository;

  @MockitoBean
  private CandleRepository candleRepository;

  @MockitoBean
  private AssetEventProducer assetEventProducer;

  @MockitoBean
  private UnadjustedScanService scanService;

  @MockitoBean
  private AdminJobRunRepository adminJobRunRepository;

  @MockitoBean
  private CandleRefreshAllPublisher refreshAllPublisher;

  @MockitoBean
  private SymbolCollector symbolCollector;

  @MockitoBean
  private JwtDecoder jwtDecoder;

  @BeforeEach
  void decodeTokens() {
    given(jwtDecoder.decode("admin-token")).willReturn(token("admin-token", "admin"));
    given(jwtDecoder.decode("user-token")).willReturn(token("user-token", "user"));
  }

  private static Jwt token(String value, String role) {
    return Jwt.withTokenValue(value).header("alg", "none").subject("user-1")
      .claim("realm_access", Map.of("roles", List.of(role))).build();
  }

  private ResultActions asAdmin(MockHttpServletRequestBuilder request) throws Exception {
    return mockMvc.perform(request.header(HttpHeaders.AUTHORIZATION, "Bearer admin-token"));
  }

  private ResultActions asAdminJson(MockHttpServletRequestBuilder request, String body) throws Exception {
    return asAdmin(request.contentType(MediaType.APPLICATION_JSON).content(body));
  }

  private static Asset asset(String symbol, boolean active) {
    return Asset.builder().symbol(symbol).name(symbol + " Inc.").country("US").currency("USD").assetType("EQUITY")
      .active(active).build();
  }

  @Test
  @DisplayName("토큰이 없으면 401, admin 역할이 없으면 403 이고 서비스까지 가지 않는다")
  void requiresAdmin() throws Exception {
    mockMvc.perform(get(BASE + "/assets")).andExpect(status().isUnauthorized());
    mockMvc.perform(get(BASE + "/assets").header(HttpHeaders.AUTHORIZATION, "Bearer user-token")).andExpect(status().isForbidden());

    verify(assetService, never()).getAllAssets();
  }

  @Nested
  @DisplayName("종목 조회")
  class ReadTests {

    @Test
    @DisplayName("전체 목록 · 키워드 검색은 서비스가 준 종목을 돌려준다")
    void listAndSearch() throws Exception {
      given(assetService.getAllAssets()).willReturn(List.of(asset("AAPL", true)));
      given(assetService.searchSymbols("Apple")).willReturn(List.of(asset("AAPL", true)));

      asAdmin(get(BASE + "/assets")).andExpect(status().isOk()).andExpect(jsonPath("$[0].symbol").value("AAPL"));
      asAdmin(get(BASE + "/assets/search").param("keyword", "Apple")).andExpect(status().isOk())
        .andExpect(jsonPath("$[0].name").value("AAPL Inc."));
    }

    @Test
    @DisplayName("미리보기 · 단건 조회는 있으면 200, 없으면 404")
    void previewAndGet() throws Exception {
      given(assetService.previewSymbol("AAPL")).willReturn(Optional.of(asset("AAPL", true)));
      given(assetService.previewSymbol("NOPE")).willReturn(Optional.empty());
      given(assetService.getAsset("AAPL")).willReturn(Optional.of(asset("AAPL", true)));
      given(assetService.getAsset("NOPE")).willReturn(Optional.empty());

      asAdmin(get(BASE + "/assets/preview/AAPL")).andExpect(status().isOk());
      asAdmin(get(BASE + "/assets/preview/NOPE")).andExpect(status().isNotFound());
      asAdmin(get(BASE + "/assets/AAPL")).andExpect(status().isOk()).andExpect(jsonPath("$.country").value("US"));
      asAdmin(get(BASE + "/assets/NOPE")).andExpect(status().isNotFound());
    }

    @Test
    @DisplayName("요약은 기본이 활성 종목 첫 페이지 20건 · 종목 코드 오름차순이고 페이지 정보를 함께 준다")
    void summaryDefaults() throws Exception {
      given(assetService.getAllAssetSummaries(any(), anyBoolean())).willReturn(
        new PageImpl<>(List.of(AssetSummaryDto.builder().symbol("AAPL").build()), PageRequest.of(0, 20), 41));

      asAdmin(get(BASE + "/assets/summary"))
        .andExpect(status().isOk())
        .andExpect(jsonPath("$.content[0].symbol").value("AAPL"))
        .andExpect(jsonPath("$.totalElements").value(41))
        .andExpect(jsonPath("$.totalPages").value(3))
        .andExpect(jsonPath("$.first").value(true));

      verify(assetService).getAllAssetSummaries(PageRequest.of(0, 20, Sort.by(Sort.Direction.ASC, "symbol")), true);
    }

    @Test
    @DisplayName("요약은 정렬 방향 · 기준 · 비활성 여부를 그대로 넘긴다")
    void summaryParams() throws Exception {
      given(assetService.getAllAssetSummaries(any(), anyBoolean())).willReturn(new PageImpl<>(List.of()));

      asAdmin(get(BASE + "/assets/summary").param("page", "1").param("size", "5").param("sortBy", "marketCap")
        .param("direction", "DESC").param("active", "false")).andExpect(status().isOk());

      verify(assetService).getAllAssetSummaries(PageRequest.of(1, 5, Sort.by(Sort.Direction.DESC, "marketCap")), false);
    }
  }

  @Nested
  @DisplayName("종목 쓰기")
  class WriteTests {

    @Test
    @DisplayName("종목 추가는 201 이고 수집 여부에 따라 안내가 다르며, 잘못된 값이면 400 과 이유를 준다")
    void createAsset() throws Exception {
      given(assetService.createAsset(eq("AAPL"), any(), any(), any(), any(), eq(true), eq(LocalDate.of(2023, 1, 1)), any(), eq(true)))
        .willReturn(asset("AAPL", true));
      given(assetService.createAsset(eq("MSFT"), any(), any(), any(), any(), eq(false), any(), any(), eq(false)))
        .willReturn(asset("MSFT", true));
      given(assetService.createAsset(eq("DUP"), any(), any(), any(), any(), anyBoolean(), any(), any(), anyBoolean()))
        .willThrow(new IllegalArgumentException("이미 있는 종목입니다: DUP"));

      asAdminJson(post(BASE + "/assets"), "{\"symbol\":\"AAPL\",\"collectData\":true,\"fromDate\":\"2023-01-01\",\"includeDividends\":true}")
        .andExpect(status().isCreated())
        .andExpect(jsonPath("$.message").value("심볼이 추가되었고 데이터 수집이 시작되었습니다."));
      asAdminJson(post(BASE + "/assets"), "{\"symbol\":\"MSFT\"}")
        .andExpect(status().isCreated())
        .andExpect(jsonPath("$.message").value("심볼이 추가되었습니다."));
      asAdminJson(post(BASE + "/assets"), "{\"symbol\":\"DUP\"}")
        .andExpect(status().isBadRequest())
        .andExpect(jsonPath("$.message").value("이미 있는 종목입니다: DUP"));
    }

    @Test
    @DisplayName("이름 수정은 200, 없는 종목은 404, 그 밖의 잘못된 값은 400")
    void updateName() throws Exception {
      given(assetService.updateAssetName("AAPL", "Apple")).willReturn(asset("AAPL", true));
      given(assetService.updateAssetName("NOPE", "X")).willThrow(new IllegalArgumentException("Not found: NOPE"));
      given(assetService.updateAssetName("AAPL", "")).willThrow(new IllegalArgumentException("이름이 비었습니다"));

      asAdminJson(put(BASE + "/assets/AAPL"), "{\"name\":\"Apple\"}").andExpect(status().isOk())
        .andExpect(jsonPath("$.message").value("종목 이름이 수정되었습니다."));
      asAdminJson(put(BASE + "/assets/NOPE"), "{\"name\":\"X\"}").andExpect(status().isNotFound());
      asAdminJson(put(BASE + "/assets/AAPL"), "{\"name\":\"\"}").andExpect(status().isBadRequest());
    }

    @Test
    @DisplayName("삭제 · 복구는 204, 없는 종목은 404")
    void deleteAndRestore() throws Exception {
      willThrow(new IllegalArgumentException("Not found: NOPE")).given(assetService).deleteAsset("NOPE");
      willThrow(new IllegalArgumentException("Not found: NOPE")).given(assetService).restoreAsset("NOPE");

      asAdmin(delete(BASE + "/assets/AAPL")).andExpect(status().isNoContent());
      asAdmin(delete(BASE + "/assets/NOPE")).andExpect(status().isNotFound());
      asAdmin(post(BASE + "/assets/AAPL/restore")).andExpect(status().isNoContent());
      asAdmin(post(BASE + "/assets/NOPE/restore")).andExpect(status().isNotFound());
      verify(assetService).deleteAsset("AAPL");
      verify(assetService).restoreAsset("AAPL");
    }

    @Test
    @DisplayName("일괄 삭제는 요청 수와 실제로 비활성화한 수를 따로 준다")
    void bulkDelete() throws Exception {
      given(assetService.deleteAssets(List.of("AAPL", "NOPE"))).willReturn(1);

      asAdminJson(post(BASE + "/assets/bulk-delete"), "{\"symbols\":[\"AAPL\",\"NOPE\"]}")
        .andExpect(status().isOk())
        .andExpect(jsonPath("$.requested").value(2))
        .andExpect(jsonPath("$.deleted").value(1))
        .andExpect(jsonPath("$.message").value("1개 종목이 비활성화되었습니다."));
      asAdminJson(post(BASE + "/assets/bulk-delete"), "{}")
        .andExpect(status().isOk())
        .andExpect(jsonPath("$.requested").value(0));
    }
  }

  @Nested
  @DisplayName("가격 · 시총 갱신")
  class UpdateTests {

    @Test
    @DisplayName("가격 갱신은 성공 200, 시세 예외는 그 상태, 없는 종목 400, 외부 장애 503, 그 밖 500")
    void updatePrice() throws Exception {
      willThrow(new MarketDataException(MarketDataResponse.PRICE_DATA_NOT_FOUND)).given(assetService).updateAssetPrice("EMPTY");
      willThrow(new IllegalArgumentException("Not found")).given(assetService).updateAssetPrice("NOPE");
      willThrow(new IllegalStateException("yahoo 429")).given(assetService).updateAssetPrice("BUSY");
      willThrow(new RuntimeException("boom")).given(assetService).updateAssetPrice("BOOM");

      asAdmin(post(BASE + "/assets/AAPL/update-price")).andExpect(status().isOk())
        .andExpect(jsonPath("$.message").value("종목 가격 데이터가 업데이트되었습니다: AAPL"));
      asAdmin(post(BASE + "/assets/EMPTY/update-price")).andExpect(status().isNotFound())
        .andExpect(jsonPath("$.message").value("가격 데이터를 찾을 수 없습니다: EMPTY"));
      asAdmin(post(BASE + "/assets/NOPE/update-price")).andExpect(status().isBadRequest());
      asAdmin(post(BASE + "/assets/BUSY/update-price")).andExpect(status().isServiceUnavailable())
        .andExpect(jsonPath("$.message").value("yahoo 429"));
      asAdmin(post(BASE + "/assets/BOOM/update-price")).andExpect(status().isInternalServerError());
    }

    @Test
    @DisplayName("시총 갱신은 성공 200, 없는 종목 400, 외부 장애 503, 그 밖 500")
    void updateMarketCap() throws Exception {
      willThrow(new IllegalArgumentException("Not found")).given(assetService).updateAssetMarketCap("NOPE");
      willThrow(new IllegalStateException("yahoo 429")).given(assetService).updateAssetMarketCap("BUSY");
      willThrow(new RuntimeException("boom")).given(assetService).updateAssetMarketCap("BOOM");

      asAdmin(post(BASE + "/assets/AAPL/update-market-cap")).andExpect(status().isOk());
      asAdmin(post(BASE + "/assets/NOPE/update-market-cap")).andExpect(status().isBadRequest())
        .andExpect(jsonPath("$.message").value("종목을 찾을 수 없습니다: NOPE"));
      asAdmin(post(BASE + "/assets/BUSY/update-market-cap")).andExpect(status().isServiceUnavailable());
      asAdmin(post(BASE + "/assets/BOOM/update-market-cap")).andExpect(status().isInternalServerError());
    }

    @Test
    @DisplayName("전체 시총 · 전체 캔들 · 신규 종목 수집은 바로 200 을 주고 뒤에서 돈다. 캔들은 한 종목이 실패해도 다음 종목으로 간다")
    void backgroundJobs() throws Exception {
      given(assetService.getAllAssets()).willReturn(List.of(asset("EMPTY", true), asset("BOOM", true), asset("AAPL", true)));
      willThrow(new MarketDataException(MarketDataResponse.PRICE_DATA_NOT_FOUND)).given(assetService).updateAssetPrice("EMPTY");
      willThrow(new RuntimeException("boom")).given(assetService).updateAssetPrice("BOOM");

      asAdmin(post(BASE + "/update/market-cap")).andExpect(status().isOk());
      asAdmin(post(BASE + "/update/candles")).andExpect(status().isOk());
      asAdmin(post(BASE + "/update/symbols")).andExpect(status().isOk());

      verify(assetService, timeout(2000)).updateAllMarketCaps();
      verify(assetService, timeout(2000)).updateAssetPrice("AAPL");
      verify(symbolCollector, timeout(2000)).collectNewlyListedDaily();
    }
  }

  @Nested
  @DisplayName("분할 탐색 · 캔들 재수집")
  class CandleTests {

    @Test
    @DisplayName("분할 탐색 결과가 없으면 진행 여부만, 있으면 결과를 준다")
    void unadjusted() throws Exception {
      given(scanService.isRunning()).willReturn(true);
      given(scanService.getLast()).willReturn(null, UnadjustedScanService.Result.builder().from(LocalDate.of(2020, 1, 1))
        .count(2).symbols(List.of("NVDA", "AVGO")).finishedAt(Instant.parse("2026-10-01T00:00:00Z")).tookMillis(1500).build());

      asAdmin(get(BASE + "/candles/unadjusted")).andExpect(status().isOk())
        .andExpect(jsonPath("$.running").value(true))
        .andExpect(jsonPath("$.symbols").doesNotExist());
      asAdmin(get(BASE + "/candles/unadjusted")).andExpect(status().isOk())
        .andExpect(jsonPath("$.count").value(2))
        .andExpect(jsonPath("$.symbols[1]").value("AVGO"))
        .andExpect(jsonPath("$.finishedAt").value("2026-10-01T00:00:00Z"));
    }

    @Test
    @DisplayName("분할 탐색 시작은 202 이고 시작일을 주지 않으면 1970-01-01 부터 찾는다")
    void scanStart() throws Exception {
      asAdmin(post(BASE + "/candles/unadjusted/scan")).andExpect(status().isAccepted())
        .andExpect(jsonPath("$.from").value("1970-01-01"));

      verify(scanService).start(LocalDate.of(1970, 1, 1));
    }

    @Test
    @DisplayName("재수집은 대문자로 바꿔 찾고, 활성 종목만 발행하며 없는 종목은 따로 알려 준다")
    void refresh() throws Exception {
      Asset aapl = asset("AAPL", true);
      given(assetRepository.findById("AAPL")).willReturn(Optional.of(aapl));
      given(assetRepository.findById("DEAD")).willReturn(Optional.of(asset("DEAD", false)));
      given(assetRepository.findById("GONE")).willReturn(Optional.empty());
      LocalDate today = LocalDate.now(ZoneId.of("America/New_York"));

      asAdminJson(post(BASE + "/candles/refresh"), "{\"symbols\":[\" aapl \",\"DEAD\",\"gone\"],\"from\":\"2020-01-01\"}")
        .andExpect(status().isOk())
        .andExpect(jsonPath("$.requested").value(3))
        .andExpect(jsonPath("$.published").value(1))
        .andExpect(jsonPath("$.notFound[0]").value("GONE"))
        .andExpect(jsonPath("$.from").value("2020-01-01"));

      verify(assetEventProducer).publishAssetCreated(aapl, true, LocalDate.of(2020, 1, 1), today, false);
      verify(assetEventProducer, never()).publishAssetCreated(argThat(a -> a.getSymbol().equals("DEAD")), anyBoolean(), any(), any(), anyBoolean());
    }

    @Test
    @DisplayName("재수집 시작일을 주지 않으면 1970-01-01, 종목이 없으면 아무것도 발행하지 않는다")
    void refreshDefaults() throws Exception {
      asAdminJson(post(BASE + "/candles/refresh"), "{}")
        .andExpect(status().isOk())
        .andExpect(jsonPath("$.requested").value(0))
        .andExpect(jsonPath("$.from").value("1970-01-01"));

      verify(assetEventProducer, never()).publishAssetCreated(any(), anyBoolean(), any(), any(), anyBoolean());
    }

    @Test
    @DisplayName("전체 재수집 기록을 조회하면 마지막 실행 시각과 건수를, 기록이 없으면 빈 값을 준다")
    void lastRefreshAll() throws Exception {
      given(adminJobRunRepository.findById("candle-refresh-all"))
        .willReturn(Optional.of(new AdminJobRun("candle-refresh-all", LocalDateTime.of(2026, 10, 1, 9, 0), 10432)), Optional.empty());

      asAdmin(get(BASE + "/candles/refresh-all")).andExpect(status().isOk())
        .andExpect(jsonPath("$.published").value(10432))
        .andExpect(jsonPath("$.lastRunAt").value("2026-10-01T09:00:00"));
      asAdmin(get(BASE + "/candles/refresh-all")).andExpect(status().isOk())
        .andExpect(jsonPath("$.published").value(0));
    }

    @Test
    @DisplayName("전체 재수집은 활성 종목 수를 기록하고 발행을 넘긴 뒤 바로 돌려준다")
    void refreshAll() throws Exception {
      List<Asset> active = List.of(asset("AAPL", true), asset("MSFT", true));
      given(assetRepository.findByActiveTrue()).willReturn(active);
      given(adminJobRunRepository.save(any())).willAnswer(invocation -> invocation.getArgument(0));

      asAdmin(post(BASE + "/candles/refresh-all")).andExpect(status().isOk())
        .andExpect(jsonPath("$.published").value(2));

      verify(refreshAllPublisher).publish(eq(active), eq(LocalDate.of(1970, 1, 1)), any());
    }
  }
}
