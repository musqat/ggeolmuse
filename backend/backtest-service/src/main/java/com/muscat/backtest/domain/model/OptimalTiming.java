package com.muscat.backtest.domain.model;

import com.muscat.backtest.common.util.PriceLookup;
import com.muscat.commonlib.dto.OHLCPriceDto;
import com.muscat.commonlib.util.MoneyUtils;
import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.List;
import java.util.function.Function;

// 최적 매매 타이밍 정보
public record OptimalTiming(
    LocalDate buyDate,
    BigDecimal buyPrice,
    LocalDate sellDate,
    BigDecimal sellPrice,
    BigDecimal returnPercent
) {
    public static OptimalTiming empty() {
        return new OptimalTiming(null, null, null, null, null);
    }

    // 날짜순 일봉에서 앞서 원화 주가(종가 × 당일 환율)가 가장 낮은 날에 사서 가장 많이 오른 날에 판 시점
    // 전 구간 최저가 · 최고가를 따로 고르면 매도일이 매수일보다 앞설 수 있어 일봉마다 당일까지 최저가에 산 수익률만 봄, 내내 하락이면 첫 날 · 수익률 0
    public static OptimalTiming best(List<OHLCPriceDto> ordered, Function<LocalDate, BigDecimal> fx) {
        OHLCPriceDto minSoFar = ordered.get(0);
        OHLCPriceDto bestBuy = ordered.get(0);
        OHLCPriceDto bestSell = ordered.get(0);
        BigDecimal bestReturn = BigDecimal.ZERO;
        for (OHLCPriceDto candle : ordered) {
            if (krwPrice(candle, fx).compareTo(krwPrice(minSoFar, fx)) < 0) {
                minSoFar = candle;
            }
            BigDecimal ret = MoneyUtils.calculateReturnRate(krwPrice(minSoFar, fx), krwPrice(candle, fx));
            if (ret.compareTo(bestReturn) > 0) {
                bestReturn = ret;
                bestBuy = minSoFar;
                bestSell = candle;
            }
        }
        return new OptimalTiming(bestBuy.date(), PriceLookup.effectiveClose(bestBuy), bestSell.date(),
            PriceLookup.effectiveClose(bestSell), bestReturn);
    }

    private static BigDecimal krwPrice(OHLCPriceDto candle, Function<LocalDate, BigDecimal> fx) {
        return PriceLookup.effectiveClose(candle).multiply(fx.apply(candle.date()));
    }
}
