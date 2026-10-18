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

// 받은 배당을 저장된 배당과 맞춤. 없던 배당과 주당 금액이 바뀐 배당(분할 뒤 다시 받음)만 돌려줌
public final class DividendMerge {

  // dividend.amount 칸의 소수 자리
  private static final int DB_SCALE = 8;

  private DividendMerge() {
  }

  public static List<Dividend> changedRows(DividendRepository repository, List<DividendDto> fetched) {
    List<Dividend> rows = new ArrayList<>();
    for (Dividend fresh : MarketDataMapper.toDividends(fetched)) {
      Optional<Dividend> stored = repository.findBySymbolAndExDate(fresh.getSymbol(), fresh.getExDate());
      if (stored.isEmpty()) {
        rows.add(fresh);
      } else if (differs(stored.get().getAmount(), fresh.getAmount())) {
        stored.get().setAmount(fresh.getAmount());
        rows.add(stored.get());
      }
    }
    return rows;
  }

  private static boolean differs(BigDecimal stored, BigDecimal fetched) {
    return stored.setScale(DB_SCALE, RoundingMode.HALF_UP).compareTo(fetched.setScale(DB_SCALE, RoundingMode.HALF_UP)) != 0;
  }
}
