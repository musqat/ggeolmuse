package com.muscat.marketdata.provider.yf;

import com.muscat.marketdata.domain.entity.Asset;
import com.muscat.marketdata.provider.MarketDataProvider;
import lombok.extern.slf4j.Slf4j;
import org.springframework.context.annotation.Profile;
import org.springframework.stereotype.Component;

import java.util.List;

@Slf4j
@Component
@Profile("dev")
public class TestSymbolSource implements MarketDataProvider.SymbolSource {

    @Override
    public List<Asset> fetchSymbols() {
        log.info("테스트용 SymbolSource - data.sql 사용으로 빈 리스트 반환");
        return List.of(); // 테스트용으로 빈 리스트 반환 (data.sql로 데이터 삽입)
    }
}