package com.muscat.trade.domain.service.impl;

import com.muscat.commonlib.util.MoneyUtils;
import com.muscat.trade.common.constants.TradeConstants;
import com.muscat.trade.common.enums.response.TradeResponse;
import com.muscat.trade.common.enums.type.PriceType;
import com.muscat.trade.common.enums.type.TradeStatus;
import com.muscat.trade.common.enums.type.TradeType;
import com.muscat.trade.common.exception.NotEnoughHoldingsException;
import com.muscat.trade.common.exception.TradeException;
import com.muscat.trade.common.logging.TradeLogger;
import com.muscat.trade.common.metrics.TradeMetrics;
import com.muscat.trade.common.util.SlippagePricing;
import com.muscat.trade.common.util.TradeUtils;
import com.muscat.trade.config.TradeProperties;
import com.muscat.trade.domain.dto.request.IdempotencyKey;
import com.muscat.trade.domain.dto.request.TradingCapacityRequestDto;
import com.muscat.trade.domain.dto.response.TradeResponseDto;
import com.muscat.trade.domain.dto.response.TradingCapacityResponseDto;
import com.muscat.trade.domain.entity.Holdings;
import com.muscat.trade.domain.entity.IdempotencyRecord;
import com.muscat.trade.domain.entity.Trade;
import com.muscat.trade.domain.event.TradeSavedEvent;
import com.muscat.trade.domain.repository.HoldingsRepository;
import com.muscat.trade.domain.repository.TradeRepository;
import com.muscat.trade.domain.service.HoldingsCalculator;
import com.muscat.trade.domain.service.HoldingsRebuilder;
import com.muscat.trade.domain.service.IdempotencyGuard;
import com.muscat.trade.domain.service.MarketDataService;
import com.muscat.trade.domain.service.TradingService;
import com.muscat.trade.infra.client.dto.AccountBalanceDto;
import java.math.BigDecimal;
import java.math.RoundingMode;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.List;
import java.util.Optional;
import java.util.stream.Collectors;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.context.ApplicationEventPublisher;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Pageable;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.transaction.support.TransactionTemplate;

// 매수 · 매도 체결(시세 · 잔액 조회는 트랜잭션 밖, 체결 저장 · 매도의 보유 반영만 트랜잭션 안)
@Service
@RequiredArgsConstructor
@Slf4j
public class TradingServiceImpl implements TradingService {

  private final TradeRepository tradeRepository;
  private final HoldingsRepository holdingsRepository;
  private final MarketDataService marketDataService;
  private final TradeLogger tradeLogger;
  private final TradeProperties tradeProperties;
  private final TradeUtils tradeUtils;
  private final ApplicationEventPublisher eventPublisher;
  private final TransactionTemplate transactionTemplate;
  private final TradeMetrics tradeMetrics;
  private final IdempotencyGuard idempotencyGuard;
  private final HoldingsRebuilder holdingsRebuilder;

  @Override
  public TradeResponseDto buyStock(String userId, Long accountId, String symbol, BigDecimal quantity,
    LocalDate tradeDate, PriceType priceType, BigDecimal manualPrice, IdempotencyKey key) {
    return executeTrade(userId, accountId, symbol, quantity, tradeDate, priceType, manualPrice,
      TradeType.BUY, key);
  }

  @Override
  public TradeResponseDto sellStock(String userId, Long accountId, String symbol, BigDecimal quantity,
    LocalDate tradeDate, PriceType priceType, BigDecimal manualPrice, IdempotencyKey key) {
    return executeTrade(userId, accountId, symbol, quantity, tradeDate, priceType, manualPrice,
      TradeType.SELL, key);
  }

  @Override
  @Transactional(readOnly = true)
  public List<TradeResponseDto> getUserTrades(String userId, int page, int size) {
    Pageable pageable = PageRequest.of(page, size);
    List<Trade> trades = tradeRepository.findByUserIdOrderByExecutedAtDesc(userId, pageable)
      .getContent();
    return trades.stream()
      .map(TradeResponseDto::from)
      .collect(Collectors.toList());
  }

  @Override
  @Transactional(readOnly = true)
  public List<TradeResponseDto> getTradesBySymbol(String userId, String symbol) {
    List<Trade> trades = tradeRepository.findByUserIdAndSymbolOrderByExecutedAtDesc(userId, symbol);
    return trades.stream()
      .map(TradeResponseDto::from)
      .collect(Collectors.toList());
  }

  @Override
  public TradingCapacityResponseDto calculateBuyingCapacity(String userId,
    TradingCapacityRequestDto request) {
    log.info("매수 가능 수량 계산 시작: userId={}, accountId={}, symbol={}, tradeDate={}",
      userId, request.getAccountId(), request.getSymbol(), request.getTradeDate());

    // 계좌 잔액(USD, 계좌가 없거나 남의 계좌면 계좌 오류)
    AccountBalanceDto balance = tradeUtils.getAccountBalance(request.getAccountId());
    BigDecimal availableBalance = balance.getBalanceUsd();

    PriceType priceType = capacityPriceType(request);
    BigDecimal currentPrice = marketDataService.determineTradePrice(request.getSymbol(),
      request.getTradeDate(), priceType, request.getManualPrice());

    // 체결 때와 같은 체결가 · 총액 기준
    BigDecimal tradePrice = applySlippage(request.getSymbol(), currentPrice, balance, priceType,
      TradeType.BUY);
    BigDecimal maxShares = maxAffordableShares(availableBalance, tradePrice, balance);
    BigDecimal totalValue = amountsOf(maxShares, tradePrice, balance, TradeType.BUY).total();

    log.info("매수 가능 수량 계산 완료: symbol={}, 잔액={}, 주가={}, 체결가={}, 최대주수={}",
      request.getSymbol(), availableBalance, currentPrice, tradePrice, maxShares);

    return new TradingCapacityResponseDto(
      request.getSymbol(),
      request.getTradeDate(),
      currentPrice,
      availableBalance,
      maxShares,
      totalValue,
      "USD",
      null,  // currentHoldings (매수용이므로 null)
      null   // maxSellableShares (매수용이므로 null)
    );
  }

  @Override
  public TradingCapacityResponseDto calculateSellingCapacity(String userId,
    TradingCapacityRequestDto request) {
    log.info("매도 가능 수량 계산 시작: userId={}, accountId={}, symbol={}, tradeDate={}",
      userId, request.getAccountId(), request.getSymbol(), request.getTradeDate());

    // 수수료율 · 슬리피지율(계좌가 없거나 남의 계좌면 계좌 오류)
    AccountBalanceDto balance = tradeUtils.getAccountBalance(request.getAccountId());

    Long accountId = Long.valueOf(request.getAccountId());

    // 현재 보유 주식 수량 조회
    Optional<Holdings> holdings = holdingsRepository.findByUserIdAndAccountIdAndSymbol(
      userId, accountId, request.getSymbol());

    BigDecimal currentHoldings = holdings.map(Holdings::getTotalQuantity)
      .orElse(BigDecimal.ZERO);

    // 매도일에 팔 수 있는 수량(뒤 날짜 누적 보유까지 봄)
    BigDecimal maxSellableShares = calculateSellableQuantity(userId, accountId,
      request.getSymbol(), request.getTradeDate());

    PriceType priceType = capacityPriceType(request);
    BigDecimal currentPrice = marketDataService.determineTradePrice(request.getSymbol(),
      request.getTradeDate(), priceType, request.getManualPrice());

    // 체결 때와 같은 체결가에서 수수료를 뺀 받을 금액 기준
    BigDecimal tradePrice = applySlippage(request.getSymbol(), currentPrice, balance, priceType,
      TradeType.SELL);
    BigDecimal totalValue = amountsOf(maxSellableShares, tradePrice, balance, TradeType.SELL).total();

    log.info("매도 가능 수량 계산 완료: symbol={}, 보유량={}, 매도가능량={}, 주가={}, 체결가={}",
      request.getSymbol(), currentHoldings, maxSellableShares, currentPrice, tradePrice);

    return new TradingCapacityResponseDto(
      request.getSymbol(),
      request.getTradeDate(),
      currentPrice,
      null,  // availableBalance (매도용이므로 null)
      null,  // maxShares (매도용이므로 null)
      totalValue,
      "USD",
      currentHoldings,
      maxSellableShares
    );
  }

  // ========== 내부 메서드들 ==========

  // 주문과 같은 가격 유형(비우면 종가)
  private static PriceType capacityPriceType(TradingCapacityRequestDto request) {
    return request.getPriceType() != null ? request.getPriceType() : PriceType.CLOSE;
  }

  // 공통 거래 실행 메서드(키가 있으면 끝난 주문인지 먼저 보고, 키 충돌은 먼저 끝난 체결)
  private TradeResponseDto executeTrade(String userId, Long accountId, String symbol,
    BigDecimal quantity, LocalDate tradeDate, PriceType priceType,
    BigDecimal manualPrice, TradeType tradeType, IdempotencyKey key) {

    String fingerprint = IdempotencyKey.fingerprint(tradeType.name(), accountId, symbol, quantity, tradeDate,
      priceType, manualPrice);
    if (key != null) {
      Optional<Trade> done = idempotencyGuard.findDone(userId, key, fingerprint);
      if (done.isPresent()) {
        return TradeResponseDto.from(done.get());
      }
    }

    log.info("{} 요청: 사용자={}, 계좌={}, 종목={}, 수량={}",
      tradeType.name(), userId, accountId, symbol, quantity);

    String accountKey = String.valueOf(accountId);

    // 거래 전 검증 · 가격 · 수수료(같은 키 처음 주문이 그새 끝나 보유 · 잔액이 바뀌었으면 그 체결)
    PreparedOrder order;
    try {
      order = prepareOrder(userId, accountId, symbol, quantity, tradeDate, priceType, manualPrice, tradeType);
    } catch (TradeException e) {
      Optional<Trade> done = doneByFirstRequest(userId, key, fingerprint);
      if (done.isEmpty()) {
        throw e;
      }
      return TradeResponseDto.from(done.get());
    }
    BigDecimal tradePrice = order.tradePrice();
    TradeAmounts amounts = order.amounts();

    // 거래 실행
    Trade savedTrade;
    try {
      savedTrade = executeTradeTransaction(userId, accountId, symbol, quantity,
        tradePrice, amounts.total(), amounts.fee(), tradeDate, tradeType, key, fingerprint);
    } catch (DataIntegrityViolationException e) {
      Optional<Trade> done = doneByFirstRequest(userId, key, fingerprint);
      if (done.isEmpty()) {
        log.error("거래 DB 트랜잭션 실패: {}", e.getMessage(), e);
        throw new TradeException(TradeResponse.TRANSACTION_FAILED);
      }
      return TradeResponseDto.from(done.get());
    }

    // 거래 로그 기록
    tradeLogger.logTrade(savedTrade.getId(), userId, accountKey, symbol,
      tradeType, quantity, tradePrice, amounts.fee(), amounts.total(), tradeDate);

    log.info("{} 완료: 거래ID={}, 금액={}", tradeType.name(), savedTrade.getId(), amounts.total());
    return TradeResponseDto.from(savedTrade);
  }

  // 체결가와 금액(트랜잭션 전에 정함)
  private record PreparedOrder(BigDecimal tradePrice, TradeAmounts amounts) {
  }

  // 거래 전 검증 · 가격 결정 · 수수료 계산(매수는 잔액 확인)
  private PreparedOrder prepareOrder(String userId, Long accountId, String symbol, BigDecimal quantity,
    LocalDate tradeDate, PriceType priceType, BigDecimal manualPrice, TradeType tradeType) {
    performPreTradeValidation(userId, accountId, symbol, quantity, tradeDate, tradeType);

    BigDecimal marketPrice = marketDataService.determineTradePrice(symbol, tradeDate, priceType,
      manualPrice);

    String accountKey = String.valueOf(accountId);
    AccountBalanceDto accountBalance = tradeUtils.getAccountBalance(accountKey);
    BigDecimal tradePrice = applySlippage(symbol, marketPrice, accountBalance, priceType,
      tradeType);
    TradeAmounts amounts = calculateTradeAmounts(userId, accountKey, quantity, tradePrice,
      accountBalance, tradeType);
    return new PreparedOrder(tradePrice, amounts);
  }

  // 같은 키의 처음 주문이 끝났으면 그 체결(키가 없거나 기록이 없으면 빈 값)
  private Optional<Trade> doneByFirstRequest(String userId, IdempotencyKey key, String fingerprint) {
    return key == null ? Optional.empty() : idempotencyGuard.findDone(userId, key, fingerprint);
  }

  // 거래 전 검증(지수는 주문 불가)
  private void performPreTradeValidation(String userId, Long accountId, String symbol,
    BigDecimal quantity, LocalDate tradeDate, TradeType tradeType) {
    if (symbol.startsWith(TradeConstants.INDEX_SYMBOL_PREFIX)) {
      throw new TradeException(TradeResponse.INDEX_NOT_TRADABLE);
    }
    if (tradeType == TradeType.SELL) {
      validateSellEligibility(userId, accountId, symbol, quantity, tradeDate);
    }
  }

  // 체결가에 계좌 슬리피지율 반영(직접 입력 가격은 제외)
  private BigDecimal applySlippage(String symbol, BigDecimal marketPrice,
    AccountBalanceDto accountBalance, PriceType priceType, TradeType tradeType) {
    BigDecimal rate = accountBalance.getSlippageRate();
    if (priceType == PriceType.MANUAL || rate == null
      || rate.compareTo(BigDecimal.ZERO) <= 0) {
      return marketPrice;
    }

    BigDecimal adjusted = SlippagePricing.apply(marketPrice, rate, tradeType,
      tradeProperties.getCalculation().getPricePrecision());
    log.debug("슬리피지 반영: 종목={}, 구분={}, 시장가={}, 율={}, 체결가={}",
      symbol, tradeType, marketPrice, rate, adjusted);
    return adjusted;
  }

  // 체결 금액 · 수수료 · 총액
  private record TradeAmounts(BigDecimal tradeAmount, BigDecimal fee, BigDecimal total) {
  }

  // 거래 금액 계산(매수는 잔액 확인)
  private TradeAmounts calculateTradeAmounts(String userId, String accountId, BigDecimal quantity,
    BigDecimal tradePrice, AccountBalanceDto accountBalance, TradeType tradeType) {
    TradeAmounts amounts = amountsOf(quantity, tradePrice, accountBalance, tradeType);
    if (tradeType == TradeType.BUY) {
      tradeUtils.validateBuyBalance(userId, accountId, amounts.total(), accountBalance);
    }
    tradeLogger.logFeeCalculation(accountId, amounts.tradeAmount(),
      accountBalance.getCommissionRate(), amounts.fee());
    return amounts;
  }

  // 잔액 안에 드는 가장 큰 수량(체결 때와 같은 총액 수량 × 체결가 + 수수료로 이분 탐색)
  private BigDecimal maxAffordableShares(BigDecimal balanceUsd, BigDecimal tradePrice,
    AccountBalanceDto accountBalance) {
    if (tradePrice.signum() <= 0) {
      return BigDecimal.ZERO;
    }
    long low = 0;
    long high = balanceUsd.divide(tradePrice, 0, RoundingMode.DOWN).longValueExact();
    while (low < high) {
      long mid = low + (high - low + 1) / 2;
      if (amountsOf(BigDecimal.valueOf(mid), tradePrice, accountBalance, TradeType.BUY).total()
        .compareTo(balanceUsd) <= 0) {
        low = mid;
      } else {
        high = mid - 1;
      }
    }
    return BigDecimal.valueOf(low);
  }

  // 수량 × 체결가의 금액 · 수수료 · 총액(센트 반올림)
  private TradeAmounts amountsOf(BigDecimal quantity, BigDecimal tradePrice,
    AccountBalanceDto accountBalance, TradeType tradeType) {
    BigDecimal tradeAmount = MoneyUtils.roundUsd(MoneyUtils.multiply(quantity, tradePrice));
    BigDecimal fee = MoneyUtils.roundUsd(tradeUtils.calculateFee(accountBalance, tradeAmount));
    BigDecimal total = tradeType == TradeType.BUY
      ? MoneyUtils.add(tradeAmount, fee) : MoneyUtils.subtract(tradeAmount, fee);
    return new TradeAmounts(tradeAmount, fee, MoneyUtils.roundUsd(total));
  }

  // 키 넣기(트랜잭션 첫 줄) · 체결 저장 · 보유 변경
  private Trade executeTradeTransaction(String userId, Long accountId, String symbol,
    BigDecimal quantity, BigDecimal tradePrice, BigDecimal totalAmount,
    BigDecimal fee, LocalDate tradeDate, TradeType tradeType, IdempotencyKey key, String fingerprint) {

    try {
      log.info("거래 DB 트랜잭션 시작: userId={}, symbol={}, amount={}", userId, symbol, totalAmount);
      Trade result = transactionTemplate.execute(status -> {
        IdempotencyRecord claimed = key == null ? null : idempotencyGuard.claim(userId, key, fingerprint);
        Trade saved = executeTradeDbTransaction(userId, accountId, symbol, quantity, tradePrice,
          totalAmount, fee, tradeDate, tradeType);
        if (claimed != null) {
          claimed.attachTrade(saved.getId());
        }
        // 트랜잭션 안에서 알려야 커밋 뒤 리스너가 받음
        eventPublisher.publishEvent(new TradeSavedEvent(saved));
        return saved;
      });
      log.info("거래 DB 트랜잭션 완료: tradeId={}", result.getId());
      if (result.getStatus() == TradeStatus.COMPLETED) {
        tradeMetrics.recordCompleted(result);
      }

      return result;

    } catch (TradeException e) {
      // 락을 잡은 뒤의 보유 부족 같은 도메인 오류는 원래 코드 그대로
      throw e;
    } catch (DataIntegrityViolationException e) {
      // 제약 위반은 거래 실패로 바꾸지 않고 그대로(중복 요청 키 충돌일 수 있음)
      throw e;
    } catch (Exception e) {
      log.error("거래 DB 트랜잭션 실패: {}", e.getMessage(), e);
      throw new TradeException(TradeResponse.TRANSACTION_FAILED);
    }
  }

  // 체결 저장(매도만 보유에 바로 반영, 매수는 정산된 뒤 보유에 들어감)
  private Trade executeTradeDbTransaction(String userId, Long accountId, String symbol,
    BigDecimal quantity, BigDecimal tradePrice, BigDecimal totalAmount,
    BigDecimal fee, LocalDate tradeDate, TradeType tradeType) {
    // 매도는 보유 행 잠금 뒤 매도 가능량(뒤 날짜 누적 보유 포함)을 다시 확인(잠금 밖 사전 검사로는 동시 매도가 같은 물량을 가져감)
    if (tradeType == TradeType.SELL) {
      holdingsRepository.findByUserIdAndAccountIdAndSymbolWithLock(userId, accountId, symbol);
      BigDecimal sellable = tradeRepository.calculateSellableQuantity(userId, accountId, symbol,
        tradeDate);
      if (sellable.compareTo(quantity) < 0) {
        throw new TradeException(TradeResponse.INSUFFICIENT_SELLABLE_QUANTITY);
      }
    }

    Trade trade = Trade.builder()
      .userId(userId)
      .accountId(accountId)
      .symbol(symbol)
      .tradeType(tradeType)
      .quantity(quantity)
      .price(tradePrice)
      .totalAmount(totalAmount)
      .fee(fee)
      .tradeDate(tradeDate)
      .executedAt(LocalDateTime.now())
      .status(tradeType == TradeType.BUY ? TradeStatus.PENDING : TradeStatus.COMPLETED)
      .build();

    Trade savedTrade = tradeRepository.save(trade);
    if (tradeType == TradeType.SELL) {
      applySellToHoldings(savedTrade);
    }

    return savedTrade;
  }

  // 뒤 거래일 체결이 있는 과거 날짜 매도는 보유를 거래일 순서로 다시 쌓고, 아니면 지금 보유에서 뺌
  private void applySellToHoldings(Trade sell) {
    if (tradeRepository.existsByUserIdAndAccountIdAndSymbolAndStatusAndTradeDateAfter(sell.getUserId(),
      sell.getAccountId(), sell.getSymbol(), TradeStatus.COMPLETED, sell.getTradeDate())) {
      holdingsRebuilder.rebuild(sell.getUserId(), sell.getAccountId(), sell.getSymbol());
      return;
    }
    updateHoldings(sell);
  }

  // 체결을 보유에 반영 (비관적 Lock)
  private void updateHoldings(Trade trade) {
    Optional<Holdings> existing = holdingsRepository.findByUserIdAndAccountIdAndSymbolWithLock(
      trade.getUserId(), trade.getAccountId(), trade.getSymbol());
    BigDecimal oldQuantity = existing.map(Holdings::getTotalQuantity).orElse(BigDecimal.ZERO);
    BigDecimal oldAvgPrice = existing.map(Holdings::getAvgPurchasePrice).orElse(BigDecimal.ZERO);

    HoldingsCalculator.Applied applied = HoldingsCalculator.apply(existing.orElse(null), trade,
      tradeProperties.getCalculation().getPricePrecision());
    if (applied.uncovered()) {
      log.error("매도 수량이 보유보다 많음: userId={}, symbol={}", trade.getUserId(), trade.getSymbol());
      throw new TradeException(TradeResponse.INSUFFICIENT_HOLDINGS);
    }

    Holdings holdings = applied.holdings();
    if (holdings == null) {
      existing.ifPresent(holdingsRepository::delete);
    } else if (existing.isEmpty()) {
      holdingsRepository.save(holdings);
    }

    tradeLogger.logHoldingsUpdate(trade.getUserId(), String.valueOf(trade.getAccountId()),
      trade.getSymbol(), oldQuantity, holdings == null ? BigDecimal.ZERO : holdings.getTotalQuantity(),
      oldAvgPrice, holdings == null ? BigDecimal.ZERO : holdings.getAvgPurchasePrice());
  }

  // 매도 가능 여부 검증(보유량 · 매도일과 뒤 날짜 누적 보유)
  private void validateSellEligibility(String userId, Long accountId, String symbol,
    BigDecimal quantity, LocalDate sellDate) {
    // 1. 기본 보유량 확인
    Optional<Holdings> holdings = holdingsRepository
      .findByUserIdAndAccountIdAndSymbol(userId, accountId, symbol);

    if (holdings.isEmpty()) {
      log.error("매도 불가: 보유종목 없음 - userId={}, symbol={}", userId, symbol);
      throw new NotEnoughHoldingsException();
    }

    Holdings holding = holdings.get();
    if (holding.getTotalQuantity().compareTo(quantity) < 0) {
      log.error("매도 불가: 수량 부족 - userId={}, symbol={}, 보유={}, 매도시도={}",
        userId, symbol, holding.getTotalQuantity(), quantity);
      throw new NotEnoughHoldingsException();
    }

    // 2. 매도일과 뒤 날짜 누적 보유로 매도 가능 수량 계산
    BigDecimal sellableQuantity = calculateSellableQuantity(userId, accountId, symbol, sellDate);

    if (sellableQuantity.compareTo(quantity) < 0) {
      log.error("매도 불가: 시간여행 거래 - userId={}, symbol={}, 매도일={}, 매도가능량={}, 매도시도량={}",
        userId, symbol, sellDate, sellableQuantity, quantity);
      throw new TradeException(TradeResponse.INSUFFICIENT_SELLABLE_QUANTITY);
    }

    log.debug("매도 가능 확인 완료: userId={}, symbol={}, 매도일={}, 매도가능량={}, 매도량={}",
      userId, symbol, sellDate, sellableQuantity, quantity);
  }

  // 매도 가능 수량(체결을 원장 순서로 읽는 쿼리 하나)
  private BigDecimal calculateSellableQuantity(String userId, Long accountId, String symbol,
    LocalDate sellDate) {
    // DB 레벨에서 집계하여 성능 최적화
    BigDecimal sellableQuantity = tradeRepository.calculateSellableQuantity(
      userId, accountId, symbol, sellDate);

    log.debug("매도 가능 수량 계산 완료 (DB 집계): symbol={}, 매도일={}, 가능수량={}",
      symbol, sellDate, sellableQuantity);
    return sellableQuantity;
  }

}
