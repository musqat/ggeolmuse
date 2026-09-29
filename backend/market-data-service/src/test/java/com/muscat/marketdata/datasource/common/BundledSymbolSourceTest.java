package com.muscat.marketdata.datasource.common;

import static org.assertj.core.api.Assertions.assertThat;

import com.muscat.marketdata.domain.entity.Asset;
import java.util.List;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

/**
 * 이미지에 넣어 둔 CSV 목록. 외부 목록 API 가 막혔을 때 이 목록으로 버틴다.
 */
@DisplayName("번들 CSV 종목 목록")
class BundledSymbolSourceTest {

  private final BundledSymbolSource source = new BundledSymbolSource();

  @Test
  @DisplayName("NYSE 와 NASDAQ CSV 를 읽어 종목을 만든다")
  void fetch_ReadsBothCsvFiles() {
    List<Asset> assets = source.fetch();

    assertThat(assets).hasSizeGreaterThan(500);
    assertThat(assets).extracting(Asset::getSymbol).contains("AAPL", "MSFT");
  }

  @Test
  @DisplayName("만든 종목은 상장 상태에 통화가 USD 다")
  void fetch_BuildsActiveUsdAssets() {
    List<Asset> assets = source.fetch();

    assertThat(assets).allSatisfy(asset -> {
      assertThat(asset.getSymbol()).isNotBlank();
      assertThat(asset.getName()).isNotBlank();
      assertThat(asset.getActive()).isTrue();
      assertThat(asset.getCurrency()).isEqualTo("USD");
    });
  }

  @Test
  @DisplayName("없는 파일을 읽으면 빈 목록을 준다")
  void load_MissingFile_ReturnsEmpty() {
    List<Asset> assets = source.load("symbols/does-not-exist.csv");

    assertThat(assets).isEmpty();
  }
}
