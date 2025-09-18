package com.muscat.marketdata;

import org.junit.jupiter.api.Test;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.context.annotation.Import;
import org.springframework.test.context.ActiveProfiles;

@SpringBootTest
@ActiveProfiles("test")
@Import(MarketDataTestConfiguration.class)
class MarketDataApplicationTests {

    @Test
    void contextLoads() {
        // 컨텍스트 로드 테스트
    }
}