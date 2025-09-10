package com.muscat.trade.domain.service.impl;

import com.muscat.trade.common.enums.type.PriceType;
import com.muscat.trade.common.enums.type.TradeType;
import com.muscat.trade.domain.entity.Holdings;
import com.muscat.trade.domain.entity.Trade;
import com.muscat.trade.domain.repository.HoldingsRepository;
import com.muscat.trade.domain.repository.HoldingsQueryRepository;
import com.muscat.trade.domain.repository.TradeRepository;
import com.muscat.trade.domain.repository.TradeQueryRepository;
import com.muscat.trade.domain.service.TradingService;
import com.muscat.trade.domain.service.MarketDataService;
import com.muscat.trade.domain.dto.response.TradeResponseDto;
import com.muscat.trade.infra.client.UserServiceClient;
import com.muscat.trade.infra.client.dto.AccountBalanceDto;
import com.muscat.trade.common.exception.TradeException;
import com.muscat.trade.common.exception.NotEnoughHoldingsException;
import com.muscat.trade.common.enums.responses.TradeResponse;
import com.muscat.trade.common.logging.TradeLogger;
import com.muscat.trade.config.TradeProperties;
import com.muscat.trade.common.util.TradeUtils;
import com.muscat.trade.common.constants.TradeConstants;
import com.muscat.commonlib.util.MoneyUtils;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Pageable;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.List;
import java.util.Optional;
import java.util.stream.Collectors;

/**
 * 거래 관련 서비스
 */
@Service
@RequiredArgsConstructor
@Transactional
@Slf4j
public class TradingServiceImpl implements TradingService {

  private final TradeRepository tradeRepository;
  private final TradeQueryRepository tradeQueryRepository;
  private final HoldingsRepository holdingsRepository;
  private final HoldingsQueryRepository holdingsQueryRepository;
  private final UserServiceClient userServiceClient;
  private final MarketDataService marketDataService;
  private final TradeLogger tradeLogger;
  private final TradeProperties tradeProperties;
  private final TradeUtils tradeUtils;

  // 주식 매수 실행 - 잔액 확인, 거래 실행, 보유종목 업데이트
  @Override
  public TradeResponseDto buyStock(String userId, Long accountId, String symbol,
                       BigDecimal quantity, LocalDate tradeDate, PriceType priceType, BigDecimal manualPrice) {
    return executeTrade(userId, accountId, symbol, quantity, tradeDate, priceType, manualPrice, TradeType.BUY);
  }

  @Override
  public TradeResponseDto sellStock(String userId, Long accountId, String symbol,
                        BigDecimal quantity, LocalDate tradeDate, PriceType priceType, BigDecimal manualPrice) {
    return executeTrade(userId, accountId, symbol, quantity, tradeDate, priceType, manualPrice, TradeType.SELL);
  }

  /**
   * 공통 거래 실행 메서드 (Template Method Pattern)
   */
  private TradeResponseDto executeTrade(String userId, Long accountId, String symbol,
                                       BigDecimal quantity, LocalDate tradeDate, PriceType priceType, 
                                       BigDecimal manualPrice, TradeType tradeType) {
    
    log.info("{} 요청: 사용자={}, 계좌={}, 종목={}, 수량={}, 거래일={}, 가격유형={}", 
        tradeType.name(), userId, accountId, symbol, quantity, tradeDate, priceType);

    // 1. 거래 전 검증 (매수/매도별 다른 로직)
    performPreTradeValidation(userId, String.valueOf(accountId), symbol, quantity, tradeDate, tradeType);

    // 2. 가격 결정
    BigDecimal tradePrice = marketDataService.determineTradePrice(symbol, tradeDate, priceType, manualPrice);
    log.info("{} 요청: 사용자={}, 계좌={}, 종목={}, 수량={}, 가격={}", 
        tradeType.name(), userId, accountId, symbol, quantity, tradePrice);

    // 3. 수수료 계산 (매수/매도별 다른 계산)
    AccountBalanceDto accountBalance = tradeUtils.getAccountBalance(String.valueOf(accountId));
    BigDecimal[] amounts = calculateTradeAmounts(userId, String.valueOf(accountId), quantity, tradePrice, accountBalance, tradeType);
    BigDecimal tradeAmount = amounts[0];
    BigDecimal fee = amounts[1];
    BigDecimal totalAmount = amounts[2];

    // 4. 거래 실행 (2단계 트랜잭션)
    Trade savedTrade = executeTradeTransaction(userId, String.valueOf(accountId), symbol, quantity, 
                                             tradePrice, totalAmount, fee, tradeDate, tradeType);
    
    // 5. 거래 로그 기록
    tradeLogger.logTrade(savedTrade.getTradeId(), userId, String.valueOf(accountId), symbol,
        tradeType, quantity, tradePrice, fee, totalAmount, tradeDate);

    log.info("{} 완료: 거래ID={}, 금액={}", tradeType.name(), savedTrade.getTradeId(), totalAmount);
    return TradeResponseDto.from(savedTrade);
  }

  /**
   * 거래 전 검증 수행 (매수/매도별 다른 로직)
   */
  private void performPreTradeValidation(String userId, String accountId, String symbol,
                                        BigDecimal quantity, LocalDate tradeDate, TradeType tradeType) {
    if (tradeType == TradeType.SELL) {
      validateSellEligibility(userId, accountId, symbol, quantity, tradeDate);
    }
  }

  /**
   * 거래 금액 계산 (매수/매도별 다른 계산)
   */
  private BigDecimal[] calculateTradeAmounts(String userId, String accountId, BigDecimal quantity, 
                                           BigDecimal tradePrice, AccountBalanceDto accountBalance, TradeType tradeType) {
    // MoneyUtils를 사용한 정확한 금융 계산
    BigDecimal tradeAmount = MoneyUtils.multiply(quantity, tradePrice);
    tradeAmount = MoneyUtils.roundUsd(tradeAmount); // USD 거래 금액 정규화
    
    BigDecimal fee = tradeUtils.calculateFee(accountBalance, tradeAmount);
    fee = MoneyUtils.roundUsd(fee); // 수수료도 USD 단위로 정규화
    
    BigDecimal totalAmount;
    
    if (tradeType == TradeType.BUY) {
      totalAmount = MoneyUtils.add(tradeAmount, fee); // 매수: 수수료 추가
      tradeUtils.validateBuyBalance(userId, accountId, totalAmount, accountBalance);
    } else {
      totalAmount = MoneyUtils.subtract(tradeAmount, fee); // 매도: 수수료 차감
    }
    
    totalAmount = MoneyUtils.roundUsd(totalAmount); // 최종 금액 정규화
    
    tradeLogger.logFeeCalculation(accountId, tradeAmount, 
                                accountBalance.getCommissionRate(), fee);
    
    return new BigDecimal[]{tradeAmount, fee, totalAmount};
  }

  /**
   * 2단계 거래 트랜잭션 실행 (외부 서비스 + DB 트랜잭션)
   */
  private Trade executeTradeTransaction(String userId, String accountId, String symbol,
                                       BigDecimal quantity, BigDecimal tradePrice, BigDecimal totalAmount,
                                       BigDecimal fee, LocalDate tradeDate, TradeType tradeType) {
    // 1단계: 외부 서비스 잔액 변경
    boolean balanceUpdated = false;
    try {
      log.info("[트랜잭션 1단계] 외부 서비스 잔액 변경 시작: accountId={}, amount={}, type={}", 
               accountId, totalAmount, tradeType);
      tradeUtils.executeBalanceUpdate(accountId, totalAmount, tradeType.name(), symbol, quantity);
      balanceUpdated = true;
      log.info("[트랜잭션 1단계] 외부 서비스 잔액 변경 완료");
    } catch (Exception e) {
      log.error("[트랜잭션 1단계] 외부 서비스 잔액 변경 실패: {}", e.getMessage());
      throw new TradeException(TradeResponse.USER_SERVICE_ERROR);
    }

    // 2단계: DB 트랜잭션으로 거래 기록 및 Holdings 업데이트
    try {
      log.info("[트랜잭션 2단계] DB 트랜잭션 시작");
      Trade result = executeTradeDbTransaction(userId, accountId, symbol, quantity, tradePrice, 
                                              totalAmount, fee, tradeDate, tradeType);
      log.info("[트랜잭션 2단계] DB 트랜잭션 완료: tradeId={}", result.getTradeId());
      return result;
    } catch (Exception e) {
      log.error("[트랜잭션 2단계] DB 트랜잭션 실패: {}", e.getMessage());
      
      // DB 실패 시 보상 트랜잭션 실행
      if (balanceUpdated) {
        try {
          log.warn("[보상 트랜잭션] 시작: 외부 서비스 잔액 롤백");
          tradeUtils.executeCompensationTransaction(accountId, totalAmount, tradeType.name(), symbol, quantity);
          log.info("[보상 트랜잭션] 완료: 외부 서비스 잔액 롤백 성공");
        } catch (Exception compensationException) {
          log.error("[보상 트랜잭션] 실패: {}. 수동 개입 필요!", compensationException.getMessage());
          // 보상 트랜잭션도 실패한 경우 알림이나 수동 처리 필요
          throw new TradeException(TradeResponse.COMPENSATION_TRANSACTION_FAILED);
        }
      }
      
      String errorMessage = tradeType == TradeType.BUY ? 
          "매수 거래 실패" : "매도 거래 실패";
      throw new TradeException(TradeResponse.TRANSACTION_FAILED);
    }
  }

  @Override
  @Transactional(readOnly = true)
  public List<TradeResponseDto> getUserTrades(String userId, int page, int size) {
    Pageable pageable = PageRequest.of(page, size);
    List<Trade> trades = tradeRepository.findByUserIdOrderByExecutedAtDesc(userId, pageable).getContent();
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
  public List<TradeResponseDto> getTradesByDateRange(String userId, LocalDate startDate, LocalDate endDate) {
    // QueryDSL을 사용한 복잡한 날짜 범위 쿼리
    List<Trade> trades = tradeQueryRepository.findTradesWithComplexFilters(
        userId, null, null, null, startDate, endDate, null, null, 
        PageRequest.of(0, TradeConstants.MAX_PAGE_SIZE)).getContent();
    return trades.stream()
        .map(TradeResponseDto::from)
        .collect(Collectors.toList());
  }

  @Override
  @Transactional(readOnly = true)
  public boolean canBuyStock(String userId, Long accountId, BigDecimal totalAmount) {
    try {
      var response = userServiceClient.getAccountBalance(accountId);
      if (response != null) {
        AccountBalanceDto balance = response;
        BigDecimal availableUsd = balance.getBalanceUsd();
        
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
    
    boolean canSell = holdings.get().getTotalQuantity().compareTo(quantity) >= 0;
    log.debug("매도 가능 여부: 종목={}, 보유량={}, 매도량={}, 가능={}", 
        symbol, holdings.get().getTotalQuantity(), quantity, canSell);
    
    return canSell;
  }


  // ========== 내부 메서드들 ==========

  /**
   * DB 트랜잭션으로 거래 기록 및 Holdings 업데이트 실행 (매수/매도 공통)
   */
  @Transactional(rollbackFor = Exception.class)
  protected Trade executeTradeDbTransaction(String userId, String accountId, String symbol, 
                                        BigDecimal quantity, BigDecimal tradePrice, BigDecimal totalAmount, 
                                        BigDecimal fee, LocalDate tradeDate, TradeType tradeType) {
    // 거래 기록 생성 및 저장
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

    // Holdings 업데이트 (동일 트랜잭션 내에서)
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
        
        BigDecimal currentTotalValue = holdings.getTotalQuantity().multiply(holdings.getAvgPurchasePrice());
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
        BigDecimal sellRatio = quantity.divide(holdings.getTotalQuantity(), TradeConstants.SELL_RATIO_PRECISION, RoundingMode.HALF_UP);
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
  private BigDecimal calculateSellableQuantity(String userId, String accountId, String symbol, LocalDate sellDate) {
    // DB 레벨에서 집계하여 성능 최적화
    BigDecimal sellableQuantity = tradeQueryRepository.calculateSellableQuantity(
        userId, accountId, symbol, sellDate);
    
    log.debug("매도 가능 수량 계산 완료 (DB 집계): symbol={}, 매도일={}, 가능수량={}", 
        symbol, sellDate, sellableQuantity);
    return sellableQuantity;
  }

}