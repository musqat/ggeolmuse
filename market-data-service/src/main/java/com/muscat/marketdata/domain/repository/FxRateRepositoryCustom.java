package com.muscat.marketdata.domain.repository;

import com.muscat.marketdata.domain.entity.FxRate;
import java.util.Optional;

/**
 * FxRate Repository Custom Interface
 */
public interface FxRateRepositoryCustom {

    /**
     * 최신 환율 조회 (날짜 기준 내림차순 1건)
     */
    Optional<FxRate> findLatestRate();
}
