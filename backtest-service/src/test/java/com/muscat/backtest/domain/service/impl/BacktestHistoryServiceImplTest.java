package com.muscat.backtest.domain.service.impl;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.BDDMockito.given;
import static org.mockito.BDDMockito.verify;

import com.muscat.backtest.common.enums.type.BacktestType;
import com.muscat.backtest.domain.dto.response.BacktestHistoryDto;
import com.muscat.backtest.domain.entity.BacktestHistory;
import com.muscat.backtest.domain.repository.BacktestHistoryRepository;
import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.List;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageImpl;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Pageable;

@ExtendWith(MockitoExtension.class)
@DisplayName("BacktestHistoryService 단위 테스트")
class BacktestHistoryServiceImplTest {

  @Mock
  private BacktestHistoryRepository backtestHistoryRepository;

  @InjectMocks
  private BacktestHistoryServiceImpl backtestHistoryService;

  private static final String TEST_USER_ID = "test-user@example.com";
  private static final String TEST_BACKTEST_ID = "bt-12345";

  private BacktestHistory testHistory;

  @BeforeEach
  void setUp() {
    testHistory = BacktestHistory.builder()
      .backtestId(TEST_BACKTEST_ID)
      .userId(TEST_USER_ID)
      .backtestType(BacktestType.STRATEGY_SIMULATION)
      .requestParams("{\"symbol\":\"AAPL\",\"amount\":1000}")
      .fxRateMode("auto")
      .createdAt(LocalDateTime.of(2024, 10, 15, 10, 30))
      .build();
  }

  @Nested
  @DisplayName("백테스트 히스토리 조회 테스트")
  class GetUserBacktestHistoryTests {

    @Test
    @DisplayName("사용자의 백테스트 히스토리가 페이징 처리되어 조회된다")
    void getUserBacktestHistory_WithData_Success() {
      // given
      BacktestHistory history1 = BacktestHistory.builder()
        .backtestId("bt-001")
        .userId(TEST_USER_ID)
        .backtestType(BacktestType.STRATEGY_SIMULATION)
        .requestParams("{\"strategy\":\"DCA\"}")
        .fxRateMode("auto")
        .createdAt(LocalDateTime.of(2024, 10, 15, 14, 0))
        .build();

      BacktestHistory history2 = BacktestHistory.builder()
        .backtestId("bt-002")
        .userId(TEST_USER_ID)
        .backtestType(BacktestType.COMPARISON)
        .requestParams("{\"symbols\":[\"AAPL\",\"GOOGL\"]}")
        .fxRateMode("manual")
        .createdAt(LocalDateTime.of(2024, 10, 14, 10, 0))
        .build();

      List<BacktestHistory> histories = List.of(history1, history2);
      Pageable pageable = PageRequest.of(0, 10);
      Page<BacktestHistory> historyPage = new PageImpl<>(histories, pageable, histories.size());

      given(backtestHistoryRepository.findByUserIdOrderByCreatedAtDesc(TEST_USER_ID, pageable))
        .willReturn(historyPage);

      // when
      Page<BacktestHistoryDto> result = backtestHistoryService.getUserBacktestHistory(
        TEST_USER_ID, pageable);

      // then
      assertThat(result).isNotNull();
      assertThat(result.getTotalElements()).isEqualTo(2);
      assertThat(result.getContent()).hasSize(2);

      BacktestHistoryDto dto1 = result.getContent().get(0);
      assertThat(dto1.getBacktestId()).isEqualTo("bt-001");
      assertThat(dto1.getUserId()).isEqualTo(TEST_USER_ID);
      assertThat(dto1.getBacktestType()).isEqualTo(BacktestType.STRATEGY_SIMULATION);
      assertThat(dto1.getRequestParams()).isEqualTo("{\"strategy\":\"DCA\"}");
      assertThat(dto1.getFxRateMode()).isEqualTo("auto");
      assertThat(dto1.getCreatedAt()).isEqualTo(LocalDateTime.of(2024, 10, 15, 14, 0));

      BacktestHistoryDto dto2 = result.getContent().get(1);
      assertThat(dto2.getBacktestId()).isEqualTo("bt-002");
      assertThat(dto2.getBacktestType()).isEqualTo(BacktestType.COMPARISON);
      assertThat(dto2.getFxRateMode()).isEqualTo("manual");

      verify(backtestHistoryRepository).findByUserIdOrderByCreatedAtDesc(TEST_USER_ID, pageable);
    }

    @Test
    @DisplayName("백테스트 히스토리가 없으면 빈 페이지가 반환된다")
    void getUserBacktestHistory_NoData_ReturnsEmpty() {
      // given
      Pageable pageable = PageRequest.of(0, 10);
      Page<BacktestHistory> emptyPage = new PageImpl<>(new ArrayList<>(), pageable, 0);

      given(backtestHistoryRepository.findByUserIdOrderByCreatedAtDesc(TEST_USER_ID, pageable))
        .willReturn(emptyPage);

      // when
      Page<BacktestHistoryDto> result = backtestHistoryService.getUserBacktestHistory(
        TEST_USER_ID, pageable);

      // then
      assertThat(result).isNotNull();
      assertThat(result.getTotalElements()).isZero();
      assertThat(result.getContent()).isEmpty();

      verify(backtestHistoryRepository).findByUserIdOrderByCreatedAtDesc(TEST_USER_ID, pageable);
    }

    @Test
    @DisplayName("페이징 처리가 올바르게 동작한다")
    void getUserBacktestHistory_Pagination_WorksCorrectly() {
      // given
      List<BacktestHistory> allHistories = new ArrayList<>();
      for (int i = 1; i <= 25; i++) {
        allHistories.add(BacktestHistory.builder()
          .backtestId("bt-" + String.format("%03d", i))
          .userId(TEST_USER_ID)
          .backtestType(BacktestType.STRATEGY_SIMULATION)
          .requestParams("{}")
          .fxRateMode("auto")
          .createdAt(LocalDateTime.now().minusDays(i))
          .build());
      }

      // 두 번째 페이지 (10-19번 항목)
      Pageable pageable = PageRequest.of(1, 10);
      List<BacktestHistory> secondPage = allHistories.subList(10, 20);
      Page<BacktestHistory> historyPage = new PageImpl<>(secondPage, pageable, allHistories.size());

      given(backtestHistoryRepository.findByUserIdOrderByCreatedAtDesc(TEST_USER_ID, pageable))
        .willReturn(historyPage);

      // when
      Page<BacktestHistoryDto> result = backtestHistoryService.getUserBacktestHistory(
        TEST_USER_ID, pageable);

      // then
      assertThat(result).isNotNull();
      assertThat(result.getTotalElements()).isEqualTo(25);
      assertThat(result.getContent()).hasSize(10);
      assertThat(result.getTotalPages()).isEqualTo(3);
      assertThat(result.getNumber()).isEqualTo(1); // 두 번째 페이지 (0-indexed)

      // 11번째 항목이 첫 번째로 와야 함
      assertThat(result.getContent().get(0).getBacktestId()).isEqualTo("bt-011");
    }

    @Test
    @DisplayName("여러 타입의 백테스트가 함께 조회된다")
    void getUserBacktestHistory_MultipleTypes_Success() {
      // given
      BacktestHistory strategyHistory = BacktestHistory.builder()
        .backtestId("bt-strategy")
        .userId(TEST_USER_ID)
        .backtestType(BacktestType.STRATEGY_SIMULATION)
        .requestParams("{\"type\":\"strategy\"}")
        .fxRateMode("auto")
        .createdAt(LocalDateTime.now())
        .build();

      BacktestHistory comparisonHistory = BacktestHistory.builder()
        .backtestId("bt-comparison")
        .userId(TEST_USER_ID)
        .backtestType(BacktestType.COMPARISON)
        .requestParams("{\"type\":\"comparison\"}")
        .fxRateMode("manual")
        .createdAt(LocalDateTime.now().minusHours(1))
        .build();

      BacktestHistory investmentHistory = BacktestHistory.builder()
        .backtestId("bt-investment")
        .userId(TEST_USER_ID)
        .backtestType(BacktestType.INVESTMENT_ANALYSIS)
        .requestParams("{\"type\":\"investment\"}")
        .fxRateMode("auto")
        .createdAt(LocalDateTime.now().minusHours(2))
        .build();

      List<BacktestHistory> histories = List.of(strategyHistory, comparisonHistory,
        investmentHistory);
      Pageable pageable = PageRequest.of(0, 10);
      Page<BacktestHistory> historyPage = new PageImpl<>(histories, pageable, histories.size());

      given(backtestHistoryRepository.findByUserIdOrderByCreatedAtDesc(TEST_USER_ID, pageable))
        .willReturn(historyPage);

      // when
      Page<BacktestHistoryDto> result = backtestHistoryService.getUserBacktestHistory(
        TEST_USER_ID, pageable);

      // then
      assertThat(result.getContent()).hasSize(3);
      assertThat(result.getContent())
        .extracting(BacktestHistoryDto::getBacktestType)
        .containsExactly(
          BacktestType.STRATEGY_SIMULATION,
          BacktestType.COMPARISON,
          BacktestType.INVESTMENT_ANALYSIS
        );
    }

    @Test
    @DisplayName("DTO 변환이 올바르게 수행된다")
    void getUserBacktestHistory_DtoConversion_Accurate() {
      // given
      Pageable pageable = PageRequest.of(0, 1);
      Page<BacktestHistory> historyPage = new PageImpl<>(List.of(testHistory), pageable, 1);

      given(backtestHistoryRepository.findByUserIdOrderByCreatedAtDesc(TEST_USER_ID, pageable))
        .willReturn(historyPage);

      // when
      Page<BacktestHistoryDto> result = backtestHistoryService.getUserBacktestHistory(
        TEST_USER_ID, pageable);

      // then
      assertThat(result.getContent()).hasSize(1);
      BacktestHistoryDto dto = result.getContent().get(0);

      // 모든 필드가 정확히 변환되었는지 확인
      assertThat(dto.getBacktestId()).isEqualTo(testHistory.getBacktestId());
      assertThat(dto.getUserId()).isEqualTo(testHistory.getUserId());
      assertThat(dto.getBacktestType()).isEqualTo(testHistory.getBacktestType());
      assertThat(dto.getRequestParams()).isEqualTo(testHistory.getRequestParams());
      assertThat(dto.getFxRateMode()).isEqualTo(testHistory.getFxRateMode());
      assertThat(dto.getCreatedAt()).isEqualTo(testHistory.getCreatedAt());
    }
  }
}
