package com.muscat.backtest.domain.repository;

import com.muscat.backtest.domain.entity.InvestmentBacktestResult;
import java.util.Optional;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

@Repository
public interface InvestmentBacktestResultRepository extends
    JpaRepository<InvestmentBacktestResult, String> {

  Optional<InvestmentBacktestResult> findByUserId(String userId);
}