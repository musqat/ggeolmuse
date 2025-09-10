package com.muscat.marketdata.common.util;

import com.muscat.marketdata.config.MarketDataProperties;
import com.muscat.marketdata.domain.entity.FxRate;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Component;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.time.DayOfWeek;
import java.time.LocalDate;
import java.util.ArrayList;
import java.util.List;
import java.util.Random;

// 환율 계산 및 생성 로직을 담당하는 컴포넌트
@Slf4j
@Component
@RequiredArgsConstructor
public class FxRateCalculator {

    private final MarketDataProperties properties;
    
    private static final RoundingMode RM = RoundingMode.HALF_UP;

    // USD->KRW 환율로부터 역환율(KRW->USD) 계산
    public BigDecimal calculateInverseRate(BigDecimal usdToKrw) {
        if (usdToKrw == null) {
            throw new IllegalArgumentException("환율은 필수입니다");
        }
        return BigDecimal.ONE.divide(usdToKrw, properties.getFx().getScale(), RM);
    }

    // BigDecimal 값을 설정된 소수점 자릿수로 정규화
    @Deprecated(since = "2024.12", forRemoval = true)
    public BigDecimal normalize(BigDecimal value) {
        // MoneyUtils.roundExchangeRate() 사용 권장
        if (value == null) {
            return null;
        }
        return value.setScale(properties.getFx().getScale(), RM);
    }

    // 과거 환율 데이터 생성 (시뮬레이션)
    public List<FxRate> generateHistoricalRates(LocalDate startDate, LocalDate endDate, BigDecimal baseRate) {
        log.info("과거 환율 생성 시작: {} ~ {}, 기준환율={}", startDate, endDate, baseRate);
        
        List<FxRate> rates = new ArrayList<>();
        Random random = new Random();
        BigDecimal currentRate = baseRate;

        for (LocalDate date = startDate; !date.isAfter(endDate); date = date.plusDays(1)) {
            // 영업일만 처리 (주말 제외)
            if (date.getDayOfWeek() != DayOfWeek.SATURDAY && date.getDayOfWeek() != DayOfWeek.SUNDAY) {
                // 랜덤 변동률 적용 (-2% ~ +2%)
                double changePercent = (random.nextGaussian() * 0.01); // 표준편차 1%
                BigDecimal multiplier = BigDecimal.ONE.add(BigDecimal.valueOf(changePercent));
                currentRate = currentRate.multiply(multiplier);
                
                // 정규화
                currentRate = normalize(currentRate);
                
                FxRate fxRate = FxRate.builder()
                    .date(date)
                    .rate(currentRate)
                    .build();
                    
                rates.add(fxRate);
            }
        }
        
        log.info("과거 환율 생성 완료: {} 건", rates.size());
        return rates;
    }
}