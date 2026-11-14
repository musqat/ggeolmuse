package com.muscat.trade.domain.repository;

import com.muscat.trade.domain.entity.Holdings;
import java.util.Optional;

// Holdings Repository Custom Interface
public interface HoldingsRepositoryCustom {

    // 포트폴리오 집계 정보 계산
    PortfolioSummaryProjection calculatePortfolioSummary(String userId);

    // 행 잠금으로 읽은 보유(먼저 읽혀 컨텍스트에 남은 상태는 DB 값으로 다시 채움)
    Optional<Holdings> findByUserIdAndAccountIdAndSymbolWithLock(String userId, Long accountId, String symbol);
}
