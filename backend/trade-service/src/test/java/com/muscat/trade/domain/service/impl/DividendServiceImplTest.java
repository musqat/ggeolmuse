package com.muscat.trade.domain.service.impl;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.groups.Tuple.tuple;
import static org.mockito.BDDMockito.given;

import com.muscat.trade.domain.dto.response.DividendResponseDto;
import com.muscat.trade.domain.entity.DividendPayout;
import com.muscat.trade.domain.repository.DividendPayoutRepository;
import com.muscat.trade.domain.service.DividendAmounts;
import java.math.BigDecimal;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.List;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

@ExtendWith(MockitoExtension.class)
@DisplayName("배당 내역")
class DividendServiceImplTest {

  @Mock
  private DividendPayoutRepository dividendPayoutRepository;

  @InjectMocks
  private DividendServiceImpl dividendService;

  @Test
  @DisplayName("세후 몫이 있는 배당만 몫 표에서 읽어 계좌 · 배당락일 · 금액을 싣는다")
  void getUserDividends_ZeroPayoutHidden() {
    DividendPayout payout = DividendPayout.of("user-1", 20L, "AAPL", LocalDate.of(2025, 5, 12),
      new DividendAmounts(new BigDecimal("0.25"), new BigDecimal("10"), new BigDecimal("2.50"),
        new BigDecimal("0.39"), new BigDecimal("2.11")),
      LocalDateTime.of(2026, 10, 18, 9, 0));
    given(dividendPayoutRepository.findByUserIdAndNetAmountGreaterThanOrderByExDateDesc("user-1", BigDecimal.ZERO))
      .willReturn(List.of(payout));

    List<DividendResponseDto> result = dividendService.getUserDividends("user-1");

    assertThat(result).extracting(DividendResponseDto::accountId, DividendResponseDto::symbol,
        DividendResponseDto::dividendDate, d -> d.netAmount().toPlainString(), d -> d.taxAmount().toPlainString())
      .containsExactly(tuple(20L, "AAPL", LocalDate.of(2025, 5, 12), "2.11", "0.39"));
  }
}
