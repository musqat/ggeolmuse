package com.muscat.marketdata.datasource.common;

import com.muscat.marketdata.domain.repository.AssetRepository;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Component;

// 캔들이 있는 종목이 95% 아래면 초기 수집 중으로 보는 확인
@Slf4j
@Component
@RequiredArgsConstructor
public class InitialCollectionCheck {

  private static final double THRESHOLD = 0.95;

  private final AssetRepository assetRepository;

  public boolean inProgress() {
    try {
      long totalAssets = assetRepository.count();
      long assetsWithCandles = assetRepository.countWithCandles();

      if (totalAssets == 0) {
        log.debug("[스케줄러 체크] Asset 없음, 스케줄러 실행");
        return false;
      }

      double completionRate = (double) assetsWithCandles / totalAssets;
      boolean inProgress = completionRate < THRESHOLD;

      if (inProgress) {
        log.info("[스케줄러 체크] 초기 수집 진행 중: {}/{}개 ({}%), 스케줄러 스킵",
          assetsWithCandles, totalAssets, String.format("%.1f", completionRate * 100));
      } else {
        log.debug("[스케줄러 체크] 초기 수집 완료: {}/{}개 ({}%), 스케줄러 실행",
          assetsWithCandles, totalAssets, String.format("%.1f", completionRate * 100));
      }

      return inProgress;
    } catch (Exception e) {
      log.warn("[스케줄러 체크] 초기 수집 여부 확인 실패, 안전하게 스케줄러 실행", e);
      return false;
    }
  }
}
