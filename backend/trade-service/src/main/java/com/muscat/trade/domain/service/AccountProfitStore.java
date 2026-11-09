package com.muscat.trade.domain.service;

import com.muscat.trade.domain.entity.AccountProfitDaily;
import com.muscat.trade.domain.repository.AccountProfitDailyRepository;
import java.time.LocalDate;
import java.util.List;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Transactional;

// 계좌 수익 흐름 저장분을 그 날짜부터 새 줄로 바꿈
@Component
@RequiredArgsConstructor
public class AccountProfitStore {

  private final AccountProfitDailyRepository accountProfitDailyRepository;

  // 같은 날짜 새 줄이 유니크 제약에 걸리지 않게 삭제를 insert 전에 DB 에 반영
  @Transactional
  public void replaceFrom(Long accountId, LocalDate from, List<AccountProfitDaily> rows) {
    accountProfitDailyRepository.deleteByAccountIdAndTradeDateGreaterThanEqual(accountId, from);
    accountProfitDailyRepository.flush();
    accountProfitDailyRepository.saveAll(rows);
  }
}
