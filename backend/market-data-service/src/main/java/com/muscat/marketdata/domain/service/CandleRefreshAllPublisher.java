package com.muscat.marketdata.domain.service;

import com.muscat.marketdata.domain.entity.Asset;
import com.muscat.marketdata.infra.kafka.AssetEventProducer;
import java.time.LocalDate;
import java.util.List;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.scheduling.annotation.Async;
import org.springframework.stereotype.Service;

/** 전체 다시 받기 발행을 요청 밖에서 한다. 1만 건 넘게 보내면 게이트웨이 30초를 넘긴다 */
@Slf4j
@Service
@RequiredArgsConstructor
public class CandleRefreshAllPublisher {

  private final AssetEventProducer assetEventProducer;

  @Async
  public void publish(List<Asset> assets, LocalDate from, LocalDate to) {
    long started = System.currentTimeMillis();
    for (Asset asset : assets) {
      assetEventProducer.publishAssetCreated(asset, true, from, to, false);
    }
    log.info("전체 캔들 재수집 발행 끝: {}개, {}초, from={}",
        assets.size(), (System.currentTimeMillis() - started) / 1000, from);
  }
}
