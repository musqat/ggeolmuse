package com.muscat.marketdata.datasource.alphavantage.provider;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.tuple;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.BDDMockito.given;

import com.muscat.marketdata.datasource.alphavantage.client.AlphaVantageClient;
import com.muscat.marketdata.datasource.alphavantage.client.AlphaVantageRateLimiter;
import com.muscat.marketdata.domain.entity.Asset;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.test.util.ReflectionTestUtils;

@ExtendWith(MockitoExtension.class)
@DisplayName("AlphaVantage 종목 목록")
class AlphaVantageSymbolSourceTest {

  @Mock
  private AlphaVantageClient client;

  @Mock
  private AlphaVantageRateLimiter rateLimiter;

  @InjectMocks
  private AlphaVantageSymbolSource source;

  @Test
  @DisplayName("Stock 으로 와도 이름이 펀드면 ETF, ETF 로 오면 ETF, 낱말 안에 든 etf 는 주식")
  void fetchSymbols_FundNameIsEtf() {
    ReflectionTestUtils.setField(source, "onlyMajorExchanges", true);
    given(client.getCsv(eq("LISTING_STATUS"), any())).willReturn(String.join("\n",
      "symbol,name,exchange,assetType,ipoDate,delistingDate,status",
      "YBST,GraniteShares YieldBoost Single Stock Universe ETF,NASDAQ,Stock,2025-01-01,null,Active",
      "SPY,SPDR S&P 500 ETF Trust,NYSE,ETF,1993-01-29,null,Active",
      "NFLX,Netflix Inc,NASDAQ,Stock,2002-05-23,null,Active"));

    assertThat(source.fetchSymbols()).extracting(Asset::getSymbol, Asset::getAssetType)
      .containsExactly(tuple("YBST", "ETF"), tuple("SPY", "ETF"), tuple("NFLX", "EQUITY"));
  }
}
