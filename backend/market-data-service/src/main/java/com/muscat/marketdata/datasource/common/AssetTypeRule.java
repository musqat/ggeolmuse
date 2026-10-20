package com.muscat.marketdata.datasource.common;

import java.util.regex.Pattern;

// 종목 유형 판정. 출처가 주식이라 해도 이름이 펀드면 ETF
public final class AssetTypeRule {

  // ETF 낱말 · exchange traded fund · money market. Netflix 처럼 낱말 안에 든 etf 는 안 봄
  private static final Pattern FUND_NAME =
      Pattern.compile("\\b(etf|exchange traded fund|money market)\\b", Pattern.CASE_INSENSITIVE);

  private AssetTypeRule() {
  }

  public static boolean isFundName(String name) {
    return name != null && FUND_NAME.matcher(name).find();
  }

  // 출처가 ETF 라 하거나 이름이 펀드면 ETF, 아니면 EQUITY
  public static String resolve(boolean sourceSaysEtf, String name) {
    return sourceSaysEtf || isFundName(name) ? "ETF" : "EQUITY";
  }
}
