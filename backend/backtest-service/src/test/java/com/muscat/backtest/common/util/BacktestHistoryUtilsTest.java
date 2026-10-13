package com.muscat.backtest.common.util;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatCode;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.BDDMockito.given;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.datatype.jsr310.JavaTimeModule;
import com.muscat.backtest.common.enums.type.BacktestType;
import com.muscat.backtest.domain.dto.request.SimulationRequest;
import com.muscat.backtest.domain.entity.BacktestHistory;
import com.muscat.backtest.domain.repository.BacktestHistoryRepository;
import java.math.BigDecimal;
import java.time.LocalDate;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

@ExtendWith(MockitoExtension.class)
@DisplayName("BacktestHistoryUtils 이력 저장")
class BacktestHistoryUtilsTest {

  @Mock
  private BacktestHistoryRepository repository;

  private final ObjectMapper objectMapper = new ObjectMapper().registerModule(new JavaTimeModule());

  private static SimulationRequest request(BigDecimal purchaseFx, BigDecimal currentFx) {
    return SimulationRequest.builder().symbol("AAPL").purchaseDate(LocalDate.of(2024, 1, 15))
      .investmentAmount(new BigDecimal("1000000")).purchaseFxRate(purchaseFx).currentFxRate(currentFx).build();
  }

  private BacktestHistory saved() {
    ArgumentCaptor<BacktestHistory> captor = ArgumentCaptor.forClass(BacktestHistory.class);
    verify(repository).save(captor.capture());
    return captor.getValue();
  }

  @Test
  @DisplayName("환율을 하나도 주지 않으면 auto 로, 요청은 JSON 으로 남긴다")
  void autoFx() {
    new BacktestHistoryUtils(repository, objectMapper).saveBacktestHistory("user-1", BacktestType.COMPARISON, request(null, null));

    BacktestHistory history = saved();
    assertThat(history.getUserId()).isEqualTo("user-1");
    assertThat(history.getBacktestType()).isEqualTo(BacktestType.COMPARISON);
    assertThat(history.getFxRateMode()).isEqualTo("auto");
    assertThat(history.getRequestParams()).contains("\"symbol\":\"AAPL\"");
  }

  @Test
  @DisplayName("매수 환율이나 현재 환율 중 하나라도 주면 manual")
  void manualFx() {
    BacktestHistoryUtils utils = new BacktestHistoryUtils(repository, objectMapper);

    utils.saveBacktestHistory("user-1", BacktestType.COMPARISON, request(null, new BigDecimal("1350")));

    assertThat(saved().getFxRateMode()).isEqualTo("manual");
  }

  @Test
  @DisplayName("요청을 JSON 으로 못 바꾸면 저장하지 않고 예외도 올리지 않는다")
  void jsonFailure() throws JsonProcessingException {
    ObjectMapper broken = mock(ObjectMapper.class);
    given(broken.writeValueAsString(any())).willThrow(new JsonProcessingException("boom") {});

    assertThatCode(() -> new BacktestHistoryUtils(repository, broken)
      .saveBacktestHistory("user-1", BacktestType.COMPARISON, request(null, null))).doesNotThrowAnyException();
    verify(repository, never()).save(any());
  }

  @Test
  @DisplayName("환율 모드를 못 읽으면 auto 로 저장한다")
  void fxModeFailure() throws JsonProcessingException {
    ObjectMapper broken = mock(ObjectMapper.class);
    given(broken.writeValueAsString(any())).willReturn("{}");
    given(broken.valueToTree(any())).willThrow(new IllegalArgumentException("tree"));

    new BacktestHistoryUtils(repository, broken).saveBacktestHistory("user-1", BacktestType.COMPARISON, request(BigDecimal.ONE, null));

    assertThat(saved().getFxRateMode()).isEqualTo("auto");
  }

  @Test
  @DisplayName("저장소가 실패해도 백테스트 결과에 영향이 없게 예외를 삼킨다")
  void repositoryFailure() {
    given(repository.save(any())).willThrow(new IllegalStateException("db down"));

    assertThatCode(() -> new BacktestHistoryUtils(repository, objectMapper)
      .saveBacktestHistory("user-1", BacktestType.COMPARISON, request(null, null))).doesNotThrowAnyException();
  }
}
