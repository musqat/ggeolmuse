package com.muscat.backtest.domain.model;

import com.muscat.backtest.domain.dto.request.SimulationRequest;
import com.muscat.backtest.infra.client.dto.DividendHistoryDto;
import com.muscat.backtest.infra.client.dto.FxRateDto;
import com.muscat.commonlib.dto.OHLCPriceDto;
import com.muscat.commonlib.dto.StockPriceDto;

//시뮬레이션 실행에 필요한 컨텍스트 데이터
public record SimulationContext(
    SimulationRequest request,
    OHLCPriceDto purchaseData,
    FxRateDto purchaseFxRate,
    StockPriceDto currentPrice,
    FxRateDto currentFxRate,
    DividendHistoryDto dividendHistory
) {
}
