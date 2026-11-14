package com.muscat.backtest.common.engine;

import com.muscat.backtest.common.enums.type.PortfolioTaxSale;
import java.math.BigDecimal;
import java.math.RoundingMode;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.function.Function;
import java.util.stream.IntStream;

// 포트폴리오 양도세 판매 배분: 모자란 달러를 판매 방식대로 줄마다 팔 달러(수수료 뺀 판 돈)로 나눔
final class PortfolioTaxSalePlan {

  private final PortfolioTaxSale method;
  private final BigDecimal keptAfterFee;

  // 줄 하나의 평가(USD) · 줄 금액(KRW) · 판매 순서 · 원화 원가(평가 손익을 쓰는 방식만) · 당일 팔 수 있는지
  record Candidate(BigDecimal valueUsd, BigDecimal plannedKrw, Integer saleOrder, BigDecimal costKrw,
                   boolean sellable) {}

  // 요청 줄 번호와 팔 달러
  record Sale(int line, BigDecimal usd) {}

  PortfolioTaxSalePlan(PortfolioTaxSale method, BigDecimal feeRate) {
    this.method = PortfolioTaxSale.orNone(method);
    this.keptAfterFee = feeRate == null ? BigDecimal.ONE : BigDecimal.ONE.subtract(feeRate);
  }

  // 원화 원가로 평가 손익을 보는 방식(손실 종목부터 · 이익 손실 섞기)인지
  boolean usesGain() {
    return method == PortfolioTaxSale.LOSS_FIRST || method == PortfolioTaxSale.GAIN_NEUTRAL;
  }

  // 요청 줄 순서의 후보 전부로 정한 줄 · 팔 달러(정한 순서 · 손실 종목부터는 파는 차례로, 목표 비중 · 섞기는 요청 줄 순서로)
  List<Sale> plan(List<Candidate> lines, BigDecimal needUsd, BigDecimal fx) {
    List<Integer> sellable = IntStream.range(0, lines.size()).filter(i -> lines.get(i).sellable()).boxed().toList();
    return switch (method) {
      case NONE -> List.of();
      case TARGET_WEIGHT -> overTarget(lines, sellable, needUsd);
      case CUSTOM_ORDER -> inOrder(lines, sellable.stream()
        .sorted(Comparator.comparing((Integer i) -> lines.get(i).saleOrder())).toList(), needUsd);
      case LOSS_FIRST -> inOrder(lines, byGain(sellable, gains(lines, sellable, fx)), needUsd);
      case GAIN_NEUTRAL -> gainNeutral(lines, sellable, needUsd, fx);
    };
  }

  // 차례대로 줄마다 남은 모자란 달러만큼(줄을 다 팔아도 모자라면 다음 줄)
  private List<Sale> inOrder(List<Candidate> lines, List<Integer> ordered, BigDecimal needUsd) {
    List<Sale> plan = new ArrayList<>();
    BigDecimal left = needUsd;
    for (int i : ordered) {
      if (left.signum() <= 0) {
        break;
      }
      BigDecimal take = left.min(netUsd(lines.get(i)));
      plan.add(new Sale(i, take));
      left = left.subtract(take);
    }
    return plan;
  }

  // 목표(줄 금액 비중 × 모든 줄 평가 합)보다 넘친 줄부터 넘친 만큼, 그래도 모자라면 남은 몫을 줄 금액 비중대로 나눔(줄마다 한 번, 요청 순서)
  private List<Sale> overTarget(List<Candidate> lines, List<Integer> sellable, BigDecimal needUsd) {
    List<Integer> all = IntStream.range(0, lines.size()).boxed().toList();
    BigDecimal plannedSum = sum(all, i -> lines.get(i).plannedKrw());
    BigDecimal stockUsd = sum(all, i -> lines.get(i).valueUsd());
    Function<Integer, BigDecimal> excess = i -> lines.get(i).valueUsd()
      .subtract(stockUsd.multiply(lines.get(i).plannedKrw()).divide(plannedSum, 8, RoundingMode.HALF_UP));
    List<Integer> over = sellable.stream()
      .filter(i -> excess.apply(i).signum() > 0)
      .sorted(Comparator.comparing(excess).reversed())
      .toList();
    Map<Integer, BigDecimal> taken = new HashMap<>();
    BigDecimal left = needUsd;
    for (int i : over) {
      if (left.signum() <= 0) {
        break;
      }
      BigDecimal take = excess.apply(i).multiply(keptAfterFee).min(left);
      taken.put(i, take);
      left = left.subtract(take);
    }
    spread(lines, sellable, i -> lines.get(i).plannedKrw(), left, taken);
    return inLineOrder(lines, taken);
  }

  // 손실 줄 · 이익 줄 묶음의 달러당 평가 손익 rL < 0 ≤ rG 로 이익 쪽 = 모자란 달러 × (-rL) ÷ (rG - rL), 손실 쪽은 나머지
  // 판 돈이 모자란 묶음은 전부 · 나머지는 남은 묶음, 묶음 안은 지금 가치대로, 한쪽 묶음이 없으면 평가 손익이 작은 줄부터
  private List<Sale> gainNeutral(List<Candidate> lines, List<Integer> sellable, BigDecimal needUsd, BigDecimal fx) {
    Map<Integer, BigDecimal> gains = gains(lines, sellable, fx);
    List<Integer> losers = sellable.stream().filter(i -> gains.get(i).signum() < 0).toList();
    List<Integer> winners = sellable.stream().filter(i -> gains.get(i).signum() >= 0).toList();
    if (losers.isEmpty() || winners.isEmpty()) {
      return inOrder(lines, byGain(sellable, gains), needUsd);
    }
    Function<Integer, BigDecimal> net = i -> netUsd(lines.get(i));
    BigDecimal lossUsd = sum(losers, net);
    BigDecimal gainUsd = sum(winners, net);
    BigDecimal lossRate = sum(losers, gains::get).divide(lossUsd, 10, RoundingMode.HALF_UP);
    BigDecimal gainRate = sum(winners, gains::get).divide(gainUsd, 10, RoundingMode.HALF_UP);
    BigDecimal toWinners = needUsd.multiply(lossRate.negate())
      .divide(gainRate.subtract(lossRate), 8, RoundingMode.HALF_UP)
      .max(needUsd.subtract(lossUsd))
      .min(gainUsd);
    Map<Integer, BigDecimal> taken = new HashMap<>();
    spread(lines, winners, net, toWinners, taken);
    spread(lines, losers, net, needUsd.subtract(toWinners), taken);
    return inLineOrder(lines, taken);
  }

  // 남은 달러를 줄 무게대로 나눠 더함, 몫이 남은 판 돈 이상인 줄은 남은 판 돈만큼 채우고 나머지를 남은 줄에 다시 나눔
  private void spread(List<Candidate> lines, List<Integer> candidates, Function<Integer, BigDecimal> weight,
    BigDecimal needUsd, Map<Integer, BigDecimal> taken) {
    List<Integer> open = new ArrayList<>(candidates);
    BigDecimal left = needUsd;
    while (left.signum() > 0 && !open.isEmpty()) {
      BigDecimal total = sum(open, weight);
      BigDecimal round = left;
      Function<Integer, BigDecimal> part = i -> round.multiply(weight.apply(i))
        .divide(total, 8, RoundingMode.CEILING);
      List<Integer> capped = open.stream()
        .filter(i -> part.apply(i).compareTo(room(lines, i, taken)) >= 0)
        .toList();
      if (capped.isEmpty()) {
        open.forEach(i -> taken.merge(i, part.apply(i), BigDecimal::add));
        return;
      }
      for (int i : capped) {
        BigDecimal room = room(lines, i, taken);
        left = left.subtract(room);
        taken.merge(i, room, BigDecimal::add);
      }
      open.removeAll(capped);
    }
  }

  // 줄의 판 돈(USD, 수수료 뺌)에서 이미 정한 몫을 뺀 값
  private BigDecimal room(List<Candidate> lines, int line, Map<Integer, BigDecimal> taken) {
    return netUsd(lines.get(line)).subtract(taken.getOrDefault(line, BigDecimal.ZERO));
  }

  // 정한 몫이 있는 줄을 요청 줄 순서로
  private static List<Sale> inLineOrder(List<Candidate> lines, Map<Integer, BigDecimal> taken) {
    return IntStream.range(0, lines.size())
      .filter(i -> taken.getOrDefault(i, BigDecimal.ZERO).signum() > 0)
      .mapToObj(i -> new Sale(i, taken.get(i)))
      .toList();
  }

  // 평가 손익(원화)이 작은 줄부터(같으면 요청 줄 순서)
  private static List<Integer> byGain(List<Integer> sellable, Map<Integer, BigDecimal> gains) {
    return sellable.stream().sorted(Comparator.comparing(gains::get)).toList();
  }

  // 팔 수 있는 줄마다 평가 손익(원화, 수수료를 뺀 판 돈 × 당일 환율 - 원화 원가)
  private Map<Integer, BigDecimal> gains(List<Candidate> lines, List<Integer> sellable, BigDecimal fx) {
    Map<Integer, BigDecimal> gains = new HashMap<>();
    sellable.forEach(i -> gains.put(i, netUsd(lines.get(i)).multiply(fx).subtract(lines.get(i).costKrw())));
    return gains;
  }

  // 평가에서 매도 수수료를 뺀 달러
  private BigDecimal netUsd(Candidate line) {
    return line.valueUsd().multiply(keptAfterFee);
  }

  private static BigDecimal sum(List<Integer> group, Function<Integer, BigDecimal> value) {
    return group.stream().map(value).reduce(BigDecimal.ZERO, BigDecimal::add);
  }
}
