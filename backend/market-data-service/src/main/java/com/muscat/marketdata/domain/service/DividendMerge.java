package com.muscat.marketdata.domain.service;

import com.muscat.marketdata.domain.dto.DividendDto;
import com.muscat.marketdata.domain.entity.Dividend;
import com.muscat.marketdata.domain.mapper.MarketDataMapper;
import com.muscat.marketdata.domain.repository.DividendRepository;
import java.math.BigDecimal;
import java.math.RoundingMode;
import java.util.ArrayList;
import java.util.List;
import java.util.Optional;

// 받은 배당을 저장된 배당과 맞춤(없던 배당과 분할 뒤 주당 금액이 바뀐 배당만 반환)
public final class DividendMerge {

  // dividend.amount 칸의 소수 자리
  private static final int DB_SCALE = 8;

  private DividendMerge() {
  }

  // 바뀐 행과 바꾸기 전 주당 금액(없던 배당은 빈 값)
  public record Change(Dividend row, BigDecimal before) {
  }

  public static List<Dividend> changedRows(DividendRepository repository, List<DividendDto> fetched) {
    return changes(repository, fetched).stream().map(Change::row).toList();
  }

  public static List<Change> changes(DividendRepository repository, List<DividendDto> fetched) {
    List<Change> changes = new ArrayList<>();
    for (Dividend fresh : MarketDataMapper.toDividends(fetched)) {
      Optional<Dividend> stored = repository.findBySymbolAndExDate(fresh.getSymbol(), fresh.getExDate());
      if (stored.isEmpty()) {
        changes.add(new Change(fresh, null));
      } else if (differs(stored.get().getAmount(), fresh.getAmount())) {
        BigDecimal before = stored.get().getAmount();
        stored.get().setAmount(fresh.getAmount());
        changes.add(new Change(stored.get(), before));
      }
    }
    return changes;
  }

  private static boolean differs(BigDecimal stored, BigDecimal fetched) {
    return stored.setScale(DB_SCALE, RoundingMode.HALF_UP).compareTo(fetched.setScale(DB_SCALE, RoundingMode.HALF_UP)) != 0;
  }
}
