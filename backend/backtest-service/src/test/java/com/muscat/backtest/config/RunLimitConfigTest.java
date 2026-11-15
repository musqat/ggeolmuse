package com.muscat.backtest.config;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.BDDMockito.given;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import com.muscat.backtest.api.controller.BacktestAnalysisController;
import com.muscat.backtest.common.enums.response.BacktestResponse;
import com.muscat.backtest.common.enums.type.StrategyType;
import com.muscat.backtest.common.exception.BacktestException;
import com.muscat.backtest.common.exception.GlobalExceptionHandler;
import com.muscat.backtest.common.metrics.BacktestMetrics;
import com.muscat.backtest.domain.dto.response.StrategyResponse;
import com.muscat.backtest.domain.service.BacktestAnalysisService;
import io.micrometer.core.instrument.simple.SimpleMeterRegistry;
import java.math.BigDecimal;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.TimeUnit;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.WebMvcTest;
import org.springframework.http.MediaType;
import org.springframework.security.oauth2.jwt.JwtDecoder;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.context.ContextConfiguration;
import org.springframework.test.context.TestPropertySource;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.ResultActions;

@WebMvcTest(BacktestAnalysisController.class)
@ContextConfiguration(classes = {BacktestAnalysisController.class, GlobalExceptionHandler.class, SecurityConfig.class,
  BacktestMetrics.class, SimpleMeterRegistry.class, RunLimitConfig.class})
@TestPropertySource(properties = {"backtest.run-limit.max-concurrent=1", "backtest.run-limit.wait=1s"})
@ActiveProfiles("test")
@DisplayName("백테스트 동시 실행 상한")
class RunLimitConfigTest {

  private static final String DCA = "{\"symbol\":\"AAPL\",\"startDate\":\"2024-01-01\",\"endDate\":\"2024-06-30\","
    + "\"monthlyAmount\":100000,\"purchaseDay\":15}";
  private static final StrategyResponse DONE = StrategyResponse.builder().symbol("AAPL")
    .strategyType(StrategyType.DCA).totalReturnPercent(BigDecimal.ONE).build();

  @Autowired
  private MockMvc mockMvc;

  @MockitoBean
  private BacktestAnalysisService backtestAnalysisService;

  @MockitoBean
  private JwtDecoder jwtDecoder;

  private ResultActions postDca() throws Exception {
    return mockMvc.perform(post("/api/analysis/strategy/dca").contentType(MediaType.APPLICATION_JSON).content(DCA));
  }

  @Test
  @DisplayName("자리가 다 찼고 기다리는 시간 안에 안 나면 429 와 TOO_MANY_BACKTESTS, 앞 실행이 끝나면 다시 받음")
  void rejectsWhileFull() throws Exception {
    CountDownLatch running = new CountDownLatch(1);
    CountDownLatch finish = new CountDownLatch(1);
    given(backtestAnalysisService.runDcaStrategy(any()))
      .willAnswer(invocation -> {
        running.countDown();
        assertThat(finish.await(5, TimeUnit.SECONDS)).isTrue();
        return DONE;
      })
      .willReturn(DONE);

    CompletableFuture<Integer> first = CompletableFuture.supplyAsync(this::dcaStatus);
    assertThat(running.await(5, TimeUnit.SECONDS)).isTrue();

    postDca()
      .andExpect(status().isTooManyRequests())
      .andExpect(jsonPath("$.errorCode").value("TOO_MANY_BACKTESTS"));

    finish.countDown();
    assertThat(first.get(5, TimeUnit.SECONDS)).isEqualTo(200);
    postDca().andExpect(status().isOk());
    verify(backtestAnalysisService, times(2)).runDcaStrategy(any());
  }

  @Test
  @DisplayName("자리가 다 찼으면 기다리다가 앞 실행이 끝나면 자리를 받아 실행")
  void waitsForSlot() throws Exception {
    CountDownLatch running = new CountDownLatch(1);
    CountDownLatch finish = new CountDownLatch(1);
    given(backtestAnalysisService.runDcaStrategy(any()))
      .willAnswer(invocation -> {
        running.countDown();
        assertThat(finish.await(5, TimeUnit.SECONDS)).isTrue();
        return DONE;
      })
      .willReturn(DONE);

    CompletableFuture<Integer> first = CompletableFuture.supplyAsync(this::dcaStatus);
    assertThat(running.await(5, TimeUnit.SECONDS)).isTrue();
    CompletableFuture<Integer> second = CompletableFuture.supplyAsync(this::dcaStatus);
    Thread.sleep(200);
    assertThat(second).isNotDone();

    finish.countDown();
    assertThat(first.get(5, TimeUnit.SECONDS)).isEqualTo(200);
    assertThat(second.get(5, TimeUnit.SECONDS)).isEqualTo(200);
    verify(backtestAnalysisService, times(2)).runDcaStrategy(any());
  }

  @Test
  @DisplayName("실행이 예외로 끝나도 자리 반환")
  void releasesOnFailure() throws Exception {
    given(backtestAnalysisService.runDcaStrategy(any()))
      .willThrow(new BacktestException(BacktestResponse.STOCK_DATA_NOT_FOUND))
      .willReturn(DONE);

    postDca().andExpect(status().isNotFound());
    postDca().andExpect(status().isOk());
  }

  // 별도 스레드에서 보낸 적립식 요청의 응답 코드
  private int dcaStatus() {
    try {
      return postDca().andReturn().getResponse().getStatus();
    } catch (Exception e) {
      throw new IllegalStateException(e);
    }
  }
}
