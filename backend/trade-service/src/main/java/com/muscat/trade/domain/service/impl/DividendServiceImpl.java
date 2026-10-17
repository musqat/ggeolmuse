package com.muscat.trade.domain.service.impl;

import com.muscat.trade.domain.dto.response.DividendResponseDto;
import com.muscat.trade.domain.repository.DividendPayoutRepository;
import com.muscat.trade.domain.service.DividendService;
import java.math.BigDecimal;
import java.util.List;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

// 배당 내역은 배당 몫 표에서 읽음. 계좌에 넣은 금액과 같은 값
@Service
@RequiredArgsConstructor
public class DividendServiceImpl implements DividendService {

  private final DividendPayoutRepository dividendPayoutRepository;

  @Override
  @Transactional(readOnly = true)
  public List<DividendResponseDto> getUserDividends(String userId) {
    return dividendPayoutRepository.findByUserIdAndNetAmountGreaterThanOrderByExDateDesc(userId, BigDecimal.ZERO)
      .stream()
      .map(DividendResponseDto::from)
      .toList();
  }
}
