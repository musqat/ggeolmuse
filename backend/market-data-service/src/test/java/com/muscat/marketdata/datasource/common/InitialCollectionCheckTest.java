package com.muscat.marketdata.datasource.common;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.BDDMockito.given;
import static org.mockito.Mockito.mock;

import com.muscat.marketdata.domain.repository.AssetRepository;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

@DisplayName("초기 수집 확인")
class InitialCollectionCheckTest {

  private final AssetRepository assetRepository = mock(AssetRepository.class);
  private final InitialCollectionCheck check = new InitialCollectionCheck(assetRepository);

  private void collected(long total, long withCandles) {
    given(assetRepository.count()).willReturn(total);
    given(assetRepository.countWithCandles()).willReturn(withCandles);
  }

  @Test
  @DisplayName("캔들 있는 종목이 95% 아래면 진행 중이다")
  void belowThreshold() {
    collected(100, 94);

    assertThat(check.inProgress()).isTrue();
  }

  @Test
  @DisplayName("95% 이상이면 끝난 것으로 본다")
  void reachedThreshold() {
    collected(100, 95);

    assertThat(check.inProgress()).isFalse();
  }

  @Test
  @DisplayName("종목이 없으면 진행 중이 아니다")
  void noAssets() {
    collected(0, 0);

    assertThat(check.inProgress()).isFalse();
  }

  @Test
  @DisplayName("확인이 실패하면 진행 중이 아닌 것으로 본다")
  void checkFails() {
    given(assetRepository.count()).willThrow(new IllegalStateException("db down"));

    assertThat(check.inProgress()).isFalse();
  }
}
