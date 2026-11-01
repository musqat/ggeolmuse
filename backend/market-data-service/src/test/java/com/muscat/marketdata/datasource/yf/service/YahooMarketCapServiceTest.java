package com.muscat.marketdata.datasource.yf.service;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.BDDMockito.given;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;

import com.muscat.marketdata.datasource.yf.client.NasdaqScreenerClient;
import com.muscat.marketdata.datasource.yf.client.NasdaqScreenerParser;
import com.muscat.marketdata.domain.entity.Asset;
import com.muscat.marketdata.domain.repository.AssetRepository;
import java.util.List;
import java.util.Optional;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

// NYSE · NASDAQ 스크리너 시총으로 보유 종목의 시총 덮어쓰기(스크리너에 없는 종목은 그대로)
@DisplayName("야후 시총 서비스")
class YahooMarketCapServiceTest {

  private final NasdaqScreenerClient client = mock(NasdaqScreenerClient.class);
  private final NasdaqScreenerParser parser = mock(NasdaqScreenerParser.class);
  private final AssetRepository assetRepository = mock(AssetRepository.class);
  private final YahooMarketCapService service = new YahooMarketCapService(client, parser, assetRepository);

  private static Asset asset(String symbol, Long marketCap) {
    return Asset.builder().symbol(symbol).marketCap(marketCap).build();
  }

  private void screener() {
    given(client.getAllStocks("nyse", "mega,large,mid,small")).willReturn("nyse");
    given(client.getAllStocks("nasdaq", "mega,large,mid,small")).willReturn("nasdaq");
    given(parser.parseStocks("nyse")).willReturn(List.of(asset("JPM", 600_000_000_000L), asset("NOCAP", null)));
    given(parser.parseStocks("nasdaq")).willReturn(List.of(asset("AAPL", 3_000_000_000_000L)));
  }

  @Test
  @DisplayName("스크리너에 시총이 있는 종목만 덮어쓰고 바꾼 수를 돌려준다")
  void updatesAll() {
    screener();
    Asset aapl = asset("AAPL", 1L);
    Asset unknown = asset("ZZZ", 5L);

    int updated = service.updateAllMarketCaps(List.of(aapl, unknown, asset("NOCAP", 7L)));

    assertThat(updated).isEqualTo(1);
    assertThat(aapl.getMarketCap()).isEqualTo(3_000_000_000_000L);
    assertThat(unknown.getMarketCap()).isEqualTo(5L);
    verify(assetRepository).save(aapl);
  }

  @Test
  @DisplayName("스크리너가 실패해 시총이 하나도 없으면 아무것도 바꾸지 않는다")
  void screenerFailure() {
    given(client.getAllStocks(anyString(), anyString())).willThrow(new IllegalStateException("blocked"));

    assertThat(service.updateAllMarketCaps(List.of(asset("AAPL", 1L)))).isZero();
    verify(assetRepository, never()).save(any());
  }

  @Test
  @DisplayName("한 종목 갱신은 대문자로 찾아 덮어쓰고, 스크리너에 없으면 false")
  void updatesOne() {
    screener();
    Asset jpm = asset("JPM", 1L);
    given(assetRepository.findById("JPM")).willReturn(Optional.of(jpm));

    assertThat(service.updateMarketCap("jpm")).isTrue();
    assertThat(jpm.getMarketCap()).isEqualTo(600_000_000_000L);
    assertThat(service.updateMarketCap("zzz")).isFalse();
  }
}
