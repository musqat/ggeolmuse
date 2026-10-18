package com.muscat.marketdata.domain.service;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.BDDMockito.given;
import static org.mockito.Mockito.mock;

import com.muscat.marketdata.domain.dto.DividendDto;
import com.muscat.marketdata.domain.entity.Dividend;
import com.muscat.marketdata.domain.repository.DividendRepository;
import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.List;
import java.util.Optional;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

@DisplayName("받은 배당을 저장된 배당과 맞춤")
class DividendMergeTest {

  private static final String SYMBOL = "NVDA";
  private static final LocalDate EX_DATE = LocalDate.of(2023, 12, 5);

  private final DividendRepository repository = mock(DividendRepository.class);

  private static DividendDto fetched(String amount) {
    return DividendDto.builder().symbol(SYMBOL).exDate(EX_DATE).amount(new BigDecimal(amount)).currency("USD").build();
  }

  private void givenStored(String amount) {
    given(repository.findBySymbolAndExDate(SYMBOL, EX_DATE)).willReturn(Optional.of(
      Dividend.builder().id(7L).symbol(SYMBOL).exDate(EX_DATE).amount(new BigDecimal(amount)).currency("USD").build()));
  }

  @Test
  @DisplayName("없던 배당은 새 행으로 돌려준다")
  void newDividend_Returned() {
    given(repository.findBySymbolAndExDate(SYMBOL, EX_DATE)).willReturn(Optional.empty());

    List<Dividend> rows = DividendMerge.changedRows(repository, List.of(fetched("0.004")));

    assertThat(rows).singleElement().satisfies(row -> {
      assertThat(row.getId()).isNull();
      assertThat(row.getAmount()).isEqualByComparingTo("0.004");
    });
  }

  @Test
  @DisplayName("분할 뒤 주당 금액이 바뀌면 저장된 행의 금액을 고쳐 돌려준다")
  void changedAmount_UpdatesStoredRow() {
    givenStored("0.04000000");

    List<Dividend> rows = DividendMerge.changedRows(repository, List.of(fetched("0.004")));

    assertThat(rows).singleElement().satisfies(row -> {
      assertThat(row.getId()).isEqualTo(7L);
      assertThat(row.getAmount()).isEqualByComparingTo("0.004");
    });
  }

  @Test
  @DisplayName("주당 금액이 같으면 돌려주지 않는다")
  void sameAmount_Skipped() {
    givenStored("0.04000000");

    assertThat(DividendMerge.changedRows(repository, List.of(fetched("0.04")))).isEmpty();
  }

  @Test
  @DisplayName("긴 소수를 저장 자리(8)로 반올림해 같으면 돌려주지 않는다")
  void sameAfterRounding_Skipped() {
    givenStored("0.09464286");

    assertThat(DividendMerge.changedRows(repository, List.of(fetched("0.0946428571")))).isEmpty();
  }
}
