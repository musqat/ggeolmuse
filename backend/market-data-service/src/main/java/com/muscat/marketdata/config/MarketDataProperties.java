package com.muscat.marketdata.config;

import java.math.BigDecimal;
import lombok.Data;
import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.stereotype.Component;

@Data
@Component
@ConfigurationProperties(prefix = "marketdata.constants")
public class MarketDataProperties {

  private Fx fx = new Fx();
  private Batch batch = new Batch();
  private Database database = new Database();
  private Calculation calculation = new Calculation();
  private FxIngest fxIngest = new FxIngest();
  private DividendYield dividendYield = new DividendYield();
  private Etf etf = new Etf();

  @Data
  public static class Fx {

    private BigDecimal defaultBaseRate = new BigDecimal("1350");
    private int scale = 6;
    private int maxFallbackDays = 7;
  }

  @Data
  public static class Calculation {

    private int percentScale = 4;  // 퍼센트 계산 소수점 자릿수
    private BigDecimal percentageMultiplier = new BigDecimal("100");
  }

  // 배당수익률 이상치 기준(%). 한 번에 종가의 specialPaymentPercent 를 넘는 배당은 합에서 빼고, 수익률이 maxYieldPercent 를 넘으면 비움
  @Data
  public static class DividendYield {
    private BigDecimal specialPaymentPercent = new BigDecimal("20");
    private BigDecimal maxYieldPercent = new BigDecimal("50");
  }

  // ETF 비활성 기준. 최근 volumeWindow 봉의 평균 거래량이 minAverageVolume 아래면 내림
  @Data
  public static class Etf {
    private int volumeWindow = 20;
    private long minAverageVolume = 10000L;
  }

  @Data
  public static class Batch {

    private int minRpm = 1;
    private long maxBackoffMs = 30000L;
    private long millisPerMinute = 60000L;
  }

  @Data
  public static class Database {

    private int symbolMaxLength = 16;
    private int currencyLength = 3;
    private int decimalPrecision = 19;
    private int decimalScale = 8;
  }

  @Data
  public static class FxIngest {

    private Backfill backfill = new Backfill();
    private Incremental incremental = new Incremental();
    private Scheduler scheduler = new Scheduler();

    @Data
    public static class Backfill {

      private boolean enabled = false;
      private int lookbackDays = 365;
    }

    @Data
    public static class Incremental {

      private boolean enabled = true;
      private int defaultDays = 30;
    }

    @Data
    public static class Scheduler {

      private boolean enabled = true;
      private String cron = "0 10 11 * * MON-FRI";
      private String zone = "Asia/Seoul";
    }
  }
}
