package com.muscat.trade.domain.service.impl;

import com.muscat.commonlib.util.MoneyUtils;
import com.muscat.trade.common.constants.TradeConstants;
import com.muscat.trade.common.enums.responses.TradeResponse;
import com.muscat.trade.common.enums.type.PriceType;
import com.muscat.trade.common.enums.type.TradeType;
import com.muscat.trade.common.exception.NotEnoughHoldingsException;
import com.muscat.trade.common.exception.TradeException;
import com.muscat.trade.common.logging.TradeLogger;
import com.muscat.trade.common.util.TradeUtils;
import com.muscat.trade.config.TradeProperties;
import com.muscat.trade.domain.dto.request.TradingCapacityRequestDto;
import com.muscat.trade.domain.dto.response.TradeResponseDto;
import com.muscat.trade.domain.dto.response.TradingCapacityResponseDto;
import com.muscat.trade.domain.entity.Holdings;
import com.muscat.trade.domain.entity.Trade;
import com.muscat.trade.domain.repository.HoldingsRepository;
import com.muscat.trade.domain.repository.TradeRepository;
import com.muscat.trade.domain.service.MarketDataService;
import com.muscat.trade.domain.service.TradingService;
import com.muscat.trade.infra.client.UserServiceClientWrapper;
import com.muscat.trade.infra.client.dto.AccountBalanceDto;
import com.muscat.trade.infra.kafka.TradeEventProducer;
import java.math.BigDecimal;
import java.math.RoundingMode;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.List;
import java.util.Optional;
import java.util.stream.Collectors;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Pageable;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
@RequiredArgsConstructor
@Transactional
@Slf4j
public class TradingServiceImpl implements TradingService {

  private final TradeRepository tradeRepository;
  private final HoldingsRepository holdingsRepository;
  private final UserServiceClientWrapper userServiceClientWrapper;
  private final MarketDataService marketDataService;
  private final TradeLogger tradeLogger;
  private final TradeProperties tradeProperties;
  private final TradeUtils tradeUtils;
  private final TradeEventProducer tradeEventProducer;

  @Override
  public TradeResponseDto buyStock(String userId, Long accountId, String symbol,
    BigDecimal quantity, LocalDate tradeDate, PriceType priceType, BigDecimal manualPrice) {
    return executeTrade(userId, accountId, symbol, quantity, tradeDate, priceType, manualPrice,
      TradeType.BUY);
  }

  @Override
  public TradeResponseDto sellStock(String userId, Long accountId, String symbol,
    BigDecimal quantity, LocalDate tradeDate, PriceType priceType, BigDecimal manualPrice) {
    return executeTrade(userId, accountId, symbol, quantity, tradeDate, priceType, manualPrice,
      TradeType.SELL);
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
  @Transactional(readOnly = true)
  public List<TradeResponseDto> getTradesByDateRange(String userId, LocalDate startDate,
    LocalDate endDate) {
    List<Trade> trades = tradeRepository.findTradesWithComplexFilters(
      userId, null, null, null, startDate, endDate, null, null,
      Pageable.unpaged()).getContent();
    return trades.stream()
      .map(TradeResponseDto::from)
      .collect(Collectors.toList());
  }

  @Override
  @Transactional(readOnly = true)
  public boolean canBuyStock(String userId, Long accountId, BigDecimal totalAmount) {
    try {
      var response = userServiceClientWrapper.getAccountBalance(accountId);
      if (response != null) {
        BigDecimal availableUsd = response.getBalanceUsd();
        log.debug("매수 가능 여부 확인: 사용자={}, 필요금액={}, 보유USD={}",
          userId, totalAmount, availableUsd);
        return availableUsd.compareTo(totalAmount) >= 0;
      }
      log.warn("계좌 잔액 조회 실패: accountId={}", accountId);
      return false;
    } catch (Exception e) {
      log.error("계좌 잔액 조회 중 오류 발생: accountId={}", accountId, e);
      return false;
    }
  }

  @Override
  @Transactional(readOnly = true)
  public boolean canSellStock(String userId, Long accountId, String symbol, BigDecimal quantity) {
    Optional<Holdings> holdings = holdingsRepository
      .findByUserIdAndAccountIdAndSymbol(userId, Long.valueOf(accountId), symbol);

    if (holdings.isEmpty()) {
      return false;
    }

    Holdings holding = holdings.get();
    boolean canSell = holding.getTotalQuantity().compareTo(quantity) >= 0;
    log.debug("매도 가능 여부: 종목={}, 보유량={}, 매도량={}, 가능={}",
      symbol, holding.getTotalQuantity(), quantity, canSell);

    return canSell;
  }

  @Override
  @Transactional(readOnly = true)
  public TradingCapacityResponseDto calculateBuyingCapacity(String userId,
    TradingCapacityRequestDto request) {
    log.info("매수 가능 수량 계산 시작: userId={}, accountId={}, symbol={}, tradeDate={}",
      userId, request.getAccountId(), request.getSymbol(), request.getTradeDate());

    try {
      // 계좌 잔액 조회 (USD)
      AccountBalanceDto balance = userServiceClientWrapper.getAccountBalance(
        Long.valueOf(request.getAccountId()));
      BigDecimal availableBalance = balance.getBalanceUsd();

      // 해당 날짜의 주식 가격 조회 (종가)
      BigDecimal currentPrice = marketDataService.getOHLCPrice(request.getSymbol(),
        request.getTradeDate(), PriceType.CLOSE);

      // 매수 가능한 최대 주식 수 계산 (소수점 버림)
      BigDecimal maxShares = availableBalance.divide(currentPrice, 0, RoundingMode.DOWN);
      BigDecimal totalValue = maxShares.multiply(currentPrice);

      log.info("매수 가능 수량 계산 완료: symbol={}, 잔액={}, 주가={}, 최대주수={}",
        request.getSymbol(), availableBalance, currentPrice, maxShares);

      return TradingCapacityResponseDto.builder()
        .symbol(request.getSymbol())
        .tradeDate(request.getTradeDate())
        .currentPrice(currentPrice)
        .availableBalance(availableBalance)
        .maxShares(maxShares)
        .totalValue(totalValue)
        .currency("USD")
        .build();

    } catch (Exception e) {
      log.error("매수 가능 수량 계산 실패: userId={}, symbol={}, error={}", userId, request.getSymbol(),
        e.getMessage(), e);
      throw new TradeException(TradeResponse.MARKET_DATA_SERVICE_ERROR);
    }
  }

  @Override
  @Transactional(readOnly = true)
  public TradingCapacityResponseDto calculateSellingCapacity(String userId,
    TradingCapacityRequestDto request) {
    log.info("매도 가능 수량 계산 시작: userId={}, accountId={}, symbol={}, tradeDate={}",
      userId, request.getAccountId(), request.getSymbol(), request.getTradeDate());

    try {
      // 현재 보유 주식 수량 조회
      Optional<Holdings> holdings = holdingsRepository.findByUserIdAndAccountIdAndSymbol(
        userId, Long.valueOf(request.getAccountId()), request.getSymbol());

      BigDecimal currentHoldings = holdings.map(Holdings::getTotalQuantity)
        .orElse(BigDecimal.ZERO);

      // FIFO 방식으로 실제 매도 가능 수량 계산
      BigDecimal maxSellableShares = calculateSellableQuantity(userId, request.getAccountId(),
        request.getSymbol(), request.getTradeDate());

      // 해당 날짜의 주식 가격 조회 (종가)
      BigDecimal currentPrice = marketDataService.getOHLCPrice(request.getSymbol(),
        request.getTradeDate(), PriceType.CLOSE);
      BigDecimal totalValue = maxSellableShares.multiply(currentPrice);

      log.info("매도 가능 수량 계산 완료: symbol={}, 보유량={}, 매도가능량={}, 주가={}",
        request.getSymbol(), currentHoldings, maxSellableShares, currentPrice);

      return TradingCapacityResponseDto.builder()
        .symbol(request.getSymbol())
        .tradeDate(request.getTradeDate())
        .currentPrice(currentPrice)
        .currentHoldings(currentHoldings)
        .maxSellableShares(maxSellableShares)
        .totalValue(totalValue)
        .currency("USD")
        .build();

    } catch (Exception e) {
      log.error("매도 가능 수량 계산 실패: userId={}, symbol={}, error={}", userId, request.getSymbol(),
        e.getMessage(), e);
      throw new TradeException(TradeResponse.MARKET_DATA_SERVICE_ERROR);
    }
  }

  // ========== 내부 메서드들 ==========

  // 공통 거래 실행 메서드
  private TradeResponseDto executeTrade(String userId, Long accountId, String symbol,
    BigDecimal quantity, LocalDate tradeDate, PriceType priceType,
    BigDecimal manualPrice, TradeType tradeType) {

    log.info("{} 요청: 사용자={}, 계좌={}, 종목={}, 수량={}",
      tradeType.name(), userId, accountId, symbol, quantity);

    // 거래 전 검증
    performPreTradeValidation(userId, String.valueOf(accountId), symbol, quantity, tradeDate,
      tradeType);

    // 가격 결정
    BigDecimal tradePrice = marketDataService.determineTradePrice(symbol, tradeDate, priceType,
      manualPrice);

    // 수수료 계산
    AccountBalanceDto accountBalance = tradeUtils.getAccountBalance(String.valueOf(accountId));
    BigDecimal[] amounts = calculateTradeAmounts(userId, String.valueOf(accountId), quantity,
      tradePrice, accountBalance, tradeType);
    BigDecimal tradeAmount = amounts[0];
    BigDecimal fee = amounts[1];
    BigDecimal totalAmount = amounts[2];

    // 거래 실행
    Trade savedTrade = executeTradeTransaction(userId, String.valueOf(accountId), symbol, quantity,
      tradePrice, totalAmount, fee, tradeDate, tradeType);

    // 거래 로그 기록
    tradeLogger.logTrade(savedTrade.getTradeId(), userId, String.valueOf(accountId), symbol,
      tradeType, quantity, tradePrice, fee, totalAmount, tradeDate);

    log.info("{} 완료: 거래ID={}, 금액={}", tradeType.name(), savedTrade.getTradeId(), totalAmount);
    return TradeResponseDto.from(savedTrade);
  }

  // 거래 전 검증
  private void performPreTradeValidation(String userId, String accountId, String symbol,
    BigDecimal quantity, LocalDate tradeDate, TradeType tradeType) {
    if (tradeType == TradeType.SELL) {
      validateSellEligibility(userId, accountId, symbol, quantity, tradeDate);
    }
  }

  // 거래 금액 계산
  private BigDecimal[] calculateTradeAmounts(String userId, String accountId, BigDecimal quantity,
    BigDecimal tradePrice, AccountBalanceDto accountBalance, TradeType tradeType) {
    BigDecimal tradeAmount = MoneyUtils.roundUsd(MoneyUtils.multiply(quantity, tradePrice));
    BigDecimal fee = MoneyUtils.roundUsd(tradeUtils.calculateFee(accountBalance, tradeAmount));

    BigDecimal totalAmount;
    if (tradeType == TradeType.BUY) {
      totalAmount = MoneyUtils.add(tradeAmount, fee);
      tradeUtils.validateBuyBalance(userId, accountId, totalAmount, accountBalance);
    } else {
      totalAmount = MoneyUtils.subtract(tradeAmount, fee);
    }

    totalAmount = MoneyUtils.roundUsd(totalAmount);
    tradeLogger.logFeeCalculation(accountId, tradeAmount, accountBalance.getCommissionRate(), fee);

    return new BigDecimal[]{tradeAmount, fee, totalAmount};
  }

  // 2단계 거래 트랜잭션 실행 (Kafka 이벤트 기반)
  private Trade executeTradeTransaction(String userId, String accountId, String symbol,
    BigDecimal quantity, BigDecimal tradePrice, BigDecimal totalAmount,
    BigDecimal fee, LocalDate tradeDate, TradeType tradeType) {

    try {
      // DB 트랜잭션으로 거래 기록 및 Holdings 업데이트
      log.info("거래 DB 트랜잭션 시작: userId={}, symbol={}, amount={}", userId, symbol, totalAmount);
      Trade result = executeTradeDbTransaction(userId, accountId, symbol, quantity, tradePrice,
        totalAmount, fee, tradeDate, tradeType);
      log.info("거래 DB 트랜잭션 완료: tradeId={}", result.getTradeId());

      // Kafka 이벤트 발행 (비동기 잔액 업데이트)
      // user-service가 TradeCompletedEvent를 소비하여 잔액 업데이트
      log.info("거래 완료 이벤트 발행: tradeId={}", result.getTradeId());
      tradeEventProducer.publishTradeCompleted(result);

      return result;

    } catch (Exception e) {
      log.error("거래 DB 트랜잭션 실패: {}", e.getMessage(), e);
      throw new TradeException(TradeResponse.TRANSACTION_FAILED);
    }

    // 1. 거래는 DB에 저장되면 무조건 성공
    // 2. user-service가 다운되어도 이벤트는 Kafka에 저장됨
    // 3. user-service 복구시 자동으로 이벤트 처리
    // 4. 만약 거래 취소가 필요하면 TradeCancelledEvent 발행
  }

  // DB 트랜잭션으로 거래 기록 및 Holdings 업데이트 실행
  @Transactional(rollbackFor = Exception.class)
  protected Trade executeTradeDbTransaction(String userId, String accountId, String symbol,
    BigDecimal quantity, BigDecimal tradePrice, BigDecimal totalAmount,
    BigDecimal fee, LocalDate tradeDate, TradeType tradeType) {
    Trade trade = Trade.builder()
      .userId(userId)
      .accountId(Long.valueOf(accountId))
      .symbol(symbol)
      .tradeType(tradeType)
      .quantity(quantity)
      .price(tradePrice)
      .totalAmount(totalAmount)
      .fee(fee)
      .tradeDate(tradeDate)
      .executedAt(LocalDateTime.now())
      .build();

    Trade savedTrade = tradeRepository.save(trade);
    updateHoldings(userId, accountId, symbol, quantity, tradePrice, totalAmount, tradeType);

    return savedTrade;
  }

  // 거래에 따른 보유 현황 업데이트 (비관적 Lock 사용)
  private void updateHoldings(String userId, String accountId, String symbol,
    BigDecimal quantity, BigDecimal price, BigDecimal totalAmount, TradeType tradeType) {

    // 비관적 Lock으로 동시성 문제 해결
    Optional<Holdings> existingHoldings = holdingsRepository
      .findByUserIdAndAccountIdAndSymbolWithLock(userId, Long.valueOf(accountId), symbol);

    if (tradeType == TradeType.BUY) {
      if (existingHoldings.isPresent()) {
        // 기존 보유 종목 업데이트 (평균 단가 재계산)
        Holdings holdings = existingHoldings.get();

        BigDecimal oldQuantity = holdings.getTotalQuantity();
        BigDecimal oldAvgPrice = holdings.getAvgPurchasePrice();

        BigDecimal currentTotalValue = holdings.getTotalQuantity()
          .multiply(holdings.getAvgPurchasePrice());
        BigDecimal newTotalValue = currentTotalValue.add(quantity.multiply(price));
        BigDecimal newTotalQuantity = holdings.getTotalQuantity().add(quantity);
        BigDecimal newAvgPrice = newTotalValue.divide(newTotalQuantity,
          tradeProperties.getCalculation().getPricePrecision(), RoundingMode.HALF_UP);

        holdings.setTotalQuantity(newTotalQuantity);
        holdings.setAvgPurchasePrice(newAvgPrice);
        holdings.setTotalInvestedAmount(holdings.getTotalInvestedAmount().add(totalAmount));

        // 보유량 변경 로그
        tradeLogger.logHoldingsUpdate(userId, accountId, symbol,
          oldQuantity, newTotalQuantity, oldAvgPrice, newAvgPrice);

        log.debug("기존 보유종목 업데이트: 종목={}, 신규평균가={}, 총보유량={}",
          symbol, newAvgPrice, newTotalQuantity);

      } else {
        // 신규 보유 종목 생성
        Holdings newHoldings = Holdings.builder()
          .userId(userId)
          .accountId(Long.valueOf(accountId))
          .symbol(symbol)
          .totalQuantity(quantity)
          .avgPurchasePrice(price)
          .totalInvestedAmount(totalAmount)
          .build();

        holdingsRepository.save(newHoldings);

        // 신규 보유량 로그
        tradeLogger.logHoldingsUpdate(userId, accountId, symbol,
          BigDecimal.ZERO, quantity, BigDecimal.ZERO, price);

        log.debug("신규 보유종목 생성: 종목={}, 매수가={}, 수량={}", symbol, price, quantity);
      }

    } else if (tradeType == TradeType.SELL) {
      if (existingHoldings.isEmpty()) {
        log.error("매도 시 보유종목 없음: userId={}, symbol={}", userId, symbol);
        throw new TradeException(TradeResponse.INSUFFICIENT_HOLDINGS);
      }

      Holdings holdings = existingHoldings.get();
      BigDecimal oldQuantity = holdings.getTotalQuantity();
      BigDecimal oldAvgPrice = holdings.getAvgPurchasePrice();

      if (holdings.getTotalQuantity().compareTo(quantity) < 0) {
        throw new TradeException(TradeResponse.INSUFFICIENT_HOLDINGS);
      }

      BigDecimal newQuantity = holdings.getTotalQuantity().subtract(quantity);

      if (newQuantity.compareTo(BigDecimal.ZERO) == 0) {
        // 전량 매도 시 보유종목 삭제
        holdingsRepository.delete(holdings);
        tradeLogger.logHoldingsUpdate(userId, accountId, symbol,
          oldQuantity, BigDecimal.ZERO, oldAvgPrice, BigDecimal.ZERO);
        log.debug("전량 매도로 보유종목 삭제: 종목={}", symbol);
      } else {
        // 부분 매도 시 수량만 업데이트 (평균단가는 유지)
        BigDecimal sellRatio = quantity.divide(holdings.getTotalQuantity(),
          TradeConstants.SELL_RATIO_PRECISION, RoundingMode.HALF_UP);
        BigDecimal soldAmount = holdings.getTotalInvestedAmount().multiply(sellRatio);

        holdings.setTotalQuantity(newQuantity);
        holdings.setTotalInvestedAmount(holdings.getTotalInvestedAmount().subtract(soldAmount));

        tradeLogger.logHoldingsUpdate(userId, accountId, symbol,
          oldQuantity, newQuantity, oldAvgPrice, holdings.getAvgPurchasePrice());

        log.debug("부분 매도로 수량 업데이트: 종목={}, 잔여수량={}", symbol, newQuantity);
      }
    }
  }

  // 매도 가능 여부 검증 (FIFO 방식 - 매도일 이전 매수 물량만 매도 가능)
  private void validateSellEligibility(String userId, String accountId, String symbol,
    BigDecimal quantity, LocalDate sellDate) {
    // 1. 기본 보유량 확인
    Optional<Holdings> holdings = holdingsRepository
      .findByUserIdAndAccountIdAndSymbol(userId, Long.valueOf(accountId), symbol);

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

    // 2. FIFO 방식으로 매도 가능 수량 계산
    BigDecimal sellableQuantity = calculateSellableQuantity(userId, accountId, symbol, sellDate);

    if (sellableQuantity.compareTo(quantity) < 0) {
      log.error("매도 불가: 시간여행 거래 - userId={}, symbol={}, 매도일={}, 매도가능량={}, 매도시도량={}",
        userId, symbol, sellDate, sellableQuantity, quantity);
      throw new TradeException(TradeResponse.INSUFFICIENT_SELLABLE_QUANTITY);
    }

    log.debug("매도 가능 확인 완료: userId={}, symbol={}, 매도일={}, 매도가능량={}, 매도량={}",
      userId, symbol, sellDate, sellableQuantity, quantity);
  }

  // FIFO 방식으로 매도 가능 수량 계산 (DB 집계 쿼리 사용)
  private BigDecimal calculateSellableQuantity(String userId, String accountId, String symbol,
    LocalDate sellDate) {
    // DB 레벨에서 집계하여 성능 최적화
    BigDecimal sellableQuantity = tradeRepository.calculateSellableQuantity(
      userId, Long.valueOf(accountId), symbol, sellDate);

    log.debug("매도 가능 수량 계산 완료 (DB 집계): symbol={}, 매도일={}, 가능수량={}",
      symbol, sellDate, sellableQuantity);
    return sellableQuantity;
  }

}
