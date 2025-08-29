package com.muscat.trade.domain.service.impl;

import com.muscat.trade.common.enums.type.PriceType;
import com.muscat.trade.common.enums.type.TradeType;
import com.muscat.trade.domain.entity.Holdings;
import com.muscat.trade.domain.entity.Trade;
import com.muscat.trade.domain.repository.HoldingsRepository;
import com.muscat.trade.domain.repository.TradeRepository;
import com.muscat.trade.domain.service.TradingService;
import com.muscat.trade.domain.dto.response.TradeResponseDto;
import com.muscat.trade.infra.client.MarketServiceClient;
import com.muscat.trade.infra.client.UserServiceClient;
import com.muscat.trade.infra.client.dto.AccountBalanceDto;
import com.muscat.trade.infra.client.dto.OHLCPriceDto;
import com.muscat.trade.common.exceptions.AccountNotFoundException;
import com.muscat.trade.common.exceptions.MarketDataException;
import com.muscat.trade.common.exceptions.NotEnoughBalanceException;
import com.muscat.trade.common.exceptions.NotEnoughHoldingsException;
import com.muscat.trade.common.logging.TransactionLogger;
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

@Service
@RequiredArgsConstructor
@Transactional
@Slf4j
public class TradingServiceImpl implements TradingService {

  private final TradeRepository tradeRepository;
  private final HoldingsRepository holdingsRepository;
  private final UserServiceClient userServiceClient;
  private final MarketServiceClient marketServiceClient;
  private final TransactionLogger transactionLogger;

  @Override
  public TradeResponseDto buyStock(String userId, String accountId, String symbol,
                       BigDecimal quantity, LocalDate tradeDate, PriceType priceType, BigDecimal manualPrice) {
    
    log.info("매수 요청: 사용자={}, 계좌={}, 종목={}, 수량={}, 거래일={}, 가격유형={}", 
        userId, accountId, symbol, quantity, tradeDate, priceType);

    // 가격 유형에 따른 거래 가격 결정
    BigDecimal tradePrice = determineTradePrice(symbol, tradeDate, priceType, manualPrice);
    
    log.info("매수 요청: 사용자={}, 계좌={}, 종목={}, 수량={}, 가격={}", 
        userId, accountId, symbol, quantity, tradePrice);

    // 계좌 정보 조회 (한 번만 조회하여 잔액 확인과 수수료 계산에 활용)
    AccountBalanceDto accountBalance = getAccountBalance(accountId);
    
    BigDecimal tradeAmount = quantity.multiply(tradePrice);
    BigDecimal fee = calculateFeeFromBalance(accountBalance, tradeAmount);
    BigDecimal totalAmount = tradeAmount.add(fee);
    
    // 매수 가능 여부 확인
    if (accountBalance.getBalanceUsd().compareTo(totalAmount) < 0) {
      transactionLogger.logBalanceCheck(userId, accountId, totalAmount, accountBalance.getBalanceUsd(), false);
      throw new NotEnoughBalanceException(totalAmount, accountBalance.getBalanceUsd());
    }
    
    transactionLogger.logBalanceCheck(userId, accountId, totalAmount, accountBalance.getBalanceUsd(), true);
    transactionLogger.logFeeCalculation(accountId, tradeAmount, accountBalance.getCommissionRate(), fee);

    // 거래 기록 생성
    Trade trade = Trade.builder()
        .userId(userId)
        .accountId(accountId)
        .symbol(symbol)
        .tradeType(TradeType.BUY)
        .quantity(quantity)
        .price(tradePrice)
        .totalAmount(totalAmount)
        .fee(fee)
        .tradeDate(tradeDate)
        .executedAt(LocalDateTime.now())
        .build();

    Trade savedTrade = tradeRepository.save(trade);

    // Holdings 업데이트
    updateHoldings(userId, accountId, symbol, quantity, tradePrice, totalAmount, TradeType.BUY);
    
    // 거래 로그 기록
    transactionLogger.logTrade(savedTrade.getTradeId(), userId, accountId, symbol,
        TradeType.BUY, quantity, tradePrice, fee, totalAmount, tradeDate);

    log.info("매수 완료: 거래ID={}, 총금액={}", savedTrade.getTradeId(), totalAmount);
    return TradeResponseDto.from(savedTrade);
  }

  @Override
  public TradeResponseDto sellStock(String userId, String accountId, String symbol,
                        BigDecimal quantity, LocalDate tradeDate, PriceType priceType, BigDecimal manualPrice) {
    
    log.info("매도 요청: 사용자={}, 계좌={}, 종목={}, 수량={}, 거래일={}, 가격유형={}", 
        userId, accountId, symbol, quantity, tradeDate, priceType);

    // 가격 유형에 따른 거래 가격 결정
    BigDecimal tradePrice = determineTradePrice(symbol, tradeDate, priceType, manualPrice);
    
    log.info("매도 요청: 사용자={}, 계좌={}, 종목={}, 수량={}, 가격={}", 
        userId, accountId, symbol, quantity, tradePrice);


    // 계좌 정보 조회 및 수수료 계산
    AccountBalanceDto accountBalance = getAccountBalance(accountId);
    BigDecimal tradeAmount = quantity.multiply(tradePrice);
    BigDecimal fee = calculateFeeFromBalance(accountBalance, tradeAmount);
    BigDecimal totalAmount = tradeAmount.subtract(fee);

    // 거래 기록 생성
    Trade trade = Trade.builder()
        .userId(userId)
        .accountId(accountId)
        .symbol(symbol)
        .tradeType(TradeType.SELL)
        .quantity(quantity)
        .price(tradePrice)
        .totalAmount(totalAmount)
        .fee(fee)
        .tradeDate(tradeDate)
        .executedAt(LocalDateTime.now())
        .build();

    Trade savedTrade = tradeRepository.save(trade);

    // Holdings 업데이트
    updateHoldings(userId, accountId, symbol, quantity, tradePrice, totalAmount, TradeType.SELL);
    
    // 계좌 잔고 업데이트 (매도 수익 추가)
    try {
      var response = userServiceClient.updateAccountBalance(accountId, totalAmount, "SELL", 
          "Stock sale: " + symbol + " x " + quantity);
      if (!response.isSuccess()) {
        log.error("계좌 잔고 업데이트 실패: accountId={}, amount={}, error={}", 
            accountId, totalAmount, response.getStatusMsg());
        throw new RuntimeException("계좌 잔고 업데이트 실패: " + response.getStatusMsg());
      }
      log.info("계좌 잔고 업데이트 성공: accountId={}, amount={}", accountId, totalAmount);
    } catch (Exception e) {
      log.error("계좌 잔고 업데이트 중 오류: accountId={}, amount={}", accountId, totalAmount, e);
      throw new RuntimeException("계좌 잔고 업데이트 실패", e);
    }
    
    // 거래 로그 기록
    transactionLogger.logTrade(savedTrade.getTradeId(), userId, accountId, symbol,
        TradeType.SELL, quantity, tradePrice, fee, totalAmount, tradeDate);

    log.info("매도 완료: 거래ID={}, 순수령액={}", savedTrade.getTradeId(), totalAmount);
    return TradeResponseDto.from(savedTrade);
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
    List<Trade> trades = tradeRepository.findTradesByDateRange(userId, startDate, endDate);
    return trades.stream()
        .map(TradeResponseDto::from)
        .collect(Collectors.toList());
  }

  @Override
  @Transactional(readOnly = true)
  public boolean canBuyStock(String userId, String accountId, BigDecimal totalAmount) {
    try {
      var response = userServiceClient.getAccountBalance(accountId);
      if (response.isSuccess() && response.getData() != null) {
        AccountBalanceDto balance = response.getData();
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
  public boolean canSellStock(String userId, String accountId, String symbol, BigDecimal quantity) {
    Optional<Holdings> holdings = holdingsRepository
        .findByUserIdAndAccountIdAndSymbol(userId, accountId, symbol);
    
    if (holdings.isEmpty()) {
      return false;
    }
    
    boolean canSell = holdings.get().getTotalQuantity().compareTo(quantity) >= 0;
    log.debug("매도 가능 여부: 종목={}, 보유량={}, 매도량={}, 가능={}", 
        symbol, holdings.get().getTotalQuantity(), quantity, canSell);
    
    return canSell;
  }


  // ========== 내부 메서드들 ==========

  // 가격 유형에 따른 거래 가격 결정
  private BigDecimal determineTradePrice(String symbol, LocalDate tradeDate, PriceType priceType, BigDecimal manualPrice) {
    if (priceType == PriceType.MANUAL) {
      // 직접입력인 경우 가격 범위 검증
      return validateManualPrice(symbol, tradeDate, manualPrice);
    } else {
      // OHLC 가격 조회
      return getOHLCPrice(symbol, tradeDate, priceType);
    }
  }

  // OHLC 가격 조회
  private BigDecimal getOHLCPrice(String symbol, LocalDate tradeDate, PriceType priceType) {
    try {
      var response = marketServiceClient.getOHLCPrice(symbol, tradeDate);
      if (response.isSuccess() && response.getData() != null) {
        OHLCPriceDto ohlc = response.getData();
        
        BigDecimal price = switch (priceType) {
          case OPEN -> ohlc.getOpenPrice();
          case HIGH -> ohlc.getHighPrice();
          case LOW -> ohlc.getLowPrice();
          case CLOSE -> ohlc.getClosePrice();
          default -> throw new IllegalArgumentException("Unsupported price type: " + priceType);
        };
        
        transactionLogger.logMarketDataRequest(symbol, priceType.getCode(), true, null);
        log.info("시장 가격 조회 성공: symbol={}, date={}, priceType={}, price={}", 
            symbol, tradeDate, priceType, price);
        return price;
      } else {
        transactionLogger.logMarketDataRequest(symbol, priceType.getCode(), false, response.getStatusMsg());
        throw new MarketDataException(symbol, response.getStatusMsg());
      }
    } catch (Exception e) {
      transactionLogger.logMarketDataRequest(symbol, priceType.getCode(), false, e.getMessage());
      log.error("시장 가격 조회 실패: symbol={}, date={}, priceType={}, error={}", 
          symbol, tradeDate, priceType, e.getMessage());
      throw new MarketDataException(symbol, "시장 가격 조회 실패: " + e.getMessage());
    }
  }

  // 직접입력 가격 검증
  private BigDecimal validateManualPrice(String symbol, LocalDate tradeDate, BigDecimal manualPrice) {
    try {
      var response = marketServiceClient.getOHLCPrice(symbol, tradeDate);
      if (response.isSuccess() && response.getData() != null) {
        OHLCPriceDto ohlc = response.getData();
        
        if (manualPrice.compareTo(ohlc.getLowPrice()) < 0 || 
            manualPrice.compareTo(ohlc.getHighPrice()) > 0) {
          
          String errorMsg = String.format("입력 가격이 범위를 벗어남: 입력=%s, 범위=%s~%s", 
              manualPrice, ohlc.getLowPrice(), ohlc.getHighPrice());
          transactionLogger.logMarketDataRequest(symbol, "MANUAL_VALIDATION", false, errorMsg);
          throw new MarketDataException(symbol, errorMsg);
        }
        
        transactionLogger.logMarketDataRequest(symbol, "MANUAL_VALIDATION", true, null);
        log.info("직접입력 가격 검증 성공: symbol={}, date={}, price={}, 범위={}~{}", 
            symbol, tradeDate, manualPrice, ohlc.getLowPrice(), ohlc.getHighPrice());
        return manualPrice;
      } else {
        transactionLogger.logMarketDataRequest(symbol, "MANUAL_VALIDATION", false, response.getStatusMsg());
        throw new MarketDataException(symbol, "가격 범위 검증 실패: " + response.getStatusMsg());
      }
    } catch (Exception e) {
      transactionLogger.logMarketDataRequest(symbol, "MANUAL_VALIDATION", false, e.getMessage());
      log.error("직접입력 가격 검증 실패: symbol={}, date={}, price={}, error={}", 
          symbol, tradeDate, manualPrice, e.getMessage());
      throw new MarketDataException(symbol, "가격 검증 실패: " + e.getMessage());
    }
  }

  // 거래에 따른 보유 현황 업데이트
  private void updateHoldings(String userId, String accountId, String symbol,
                             BigDecimal quantity, BigDecimal price, BigDecimal totalAmount, TradeType tradeType) {
    
    Optional<Holdings> existingHoldings = holdingsRepository
        .findByUserIdAndAccountIdAndSymbol(userId, accountId, symbol);

    if (tradeType == TradeType.BUY) {
      if (existingHoldings.isPresent()) {
        // 기존 보유 종목 업데이트 (평균 단가 재계산)
        Holdings holdings = existingHoldings.get();
        
        BigDecimal oldQuantity = holdings.getTotalQuantity();
        BigDecimal oldAvgPrice = holdings.getAvgPurchasePrice();
        
        BigDecimal currentTotalValue = holdings.getTotalQuantity().multiply(holdings.getAvgPurchasePrice());
        BigDecimal newTotalValue = currentTotalValue.add(quantity.multiply(price));
        BigDecimal newTotalQuantity = holdings.getTotalQuantity().add(quantity);
        BigDecimal newAvgPrice = newTotalValue.divide(newTotalQuantity, 2, RoundingMode.HALF_UP);
        
        holdings.setTotalQuantity(newTotalQuantity);
        holdings.setAvgPurchasePrice(newAvgPrice);
        holdings.setTotalInvestedAmount(holdings.getTotalInvestedAmount().add(totalAmount));
        
        // 보유량 변경 로그
        transactionLogger.logHoldingsUpdate(userId, accountId, symbol, 
            oldQuantity, newTotalQuantity, oldAvgPrice, newAvgPrice);
        
        log.debug("기존 보유종목 업데이트: 종목={}, 신규평균가={}, 총보유량={}", 
            symbol, newAvgPrice, newTotalQuantity);
        
      } else {
        // 신규 보유 종목 생성
        Holdings newHoldings = Holdings.builder()
            .userId(userId)
            .accountId(accountId)
            .symbol(symbol)
            .totalQuantity(quantity)
            .avgPurchasePrice(price)
            .totalInvestedAmount(totalAmount)
            .lastDividendCalculated(LocalDate.now())
            .build();
        
        holdingsRepository.save(newHoldings);
        
        // 신규 보유량 로그
        transactionLogger.logHoldingsUpdate(userId, accountId, symbol, 
            BigDecimal.ZERO, quantity, BigDecimal.ZERO, price);
        
        log.debug("신규 보유종목 생성: 종목={}, 매수가={}, 수량={}", symbol, price, quantity);
      }
      
    } else if (tradeType == TradeType.SELL) {
      if (existingHoldings.isEmpty()) {
        log.error("매도 시 보유종목 없음: userId={}, symbol={}", userId, symbol);
        throw new NotEnoughHoldingsException(symbol, quantity, BigDecimal.ZERO);
      }
      
      Holdings holdings = existingHoldings.get();
      BigDecimal oldQuantity = holdings.getTotalQuantity();
      BigDecimal oldAvgPrice = holdings.getAvgPurchasePrice();
      
      if (holdings.getTotalQuantity().compareTo(quantity) < 0) {
        throw new NotEnoughHoldingsException(symbol, quantity, holdings.getTotalQuantity());
      }
      
      BigDecimal newQuantity = holdings.getTotalQuantity().subtract(quantity);
      
      if (newQuantity.compareTo(BigDecimal.ZERO) == 0) {
        // 전량 매도 시 보유종목 삭제
        holdingsRepository.delete(holdings);
        transactionLogger.logHoldingsUpdate(userId, accountId, symbol, 
            oldQuantity, BigDecimal.ZERO, oldAvgPrice, BigDecimal.ZERO);
        log.debug("전량 매도로 보유종목 삭제: 종목={}", symbol);
      } else {
        // 부분 매도 시 수량만 업데이트 (평균단가는 유지)
        BigDecimal sellRatio = quantity.divide(holdings.getTotalQuantity(), 6, RoundingMode.HALF_UP);
        BigDecimal soldAmount = holdings.getTotalInvestedAmount().multiply(sellRatio);
        
        holdings.setTotalQuantity(newQuantity);
        holdings.setTotalInvestedAmount(holdings.getTotalInvestedAmount().subtract(soldAmount));
        
        transactionLogger.logHoldingsUpdate(userId, accountId, symbol, 
            oldQuantity, newQuantity, oldAvgPrice, holdings.getAvgPurchasePrice());
        
        log.debug("부분 매도로 수량 업데이트: 종목={}, 잔여수량={}", symbol, newQuantity);
      }
    }
  }


  // 계좌 정보 조회
  private AccountBalanceDto getAccountBalance(String accountId) {
    try {
      var response = userServiceClient.getAccountBalance(accountId);
      if (response.isSuccess() && response.getData() != null) {
        return response.getData();
      }
      throw new AccountNotFoundException(accountId);
    } catch (Exception e) {
      log.error("계좌 정보 조회 중 오류 발생: accountId={}", accountId, e);
      throw new AccountNotFoundException(accountId);
    }
  }

  // 계좌별 수수료율로 수수료 계산
  private BigDecimal calculateFeeFromBalance(AccountBalanceDto balance, BigDecimal tradeAmount) {
    BigDecimal commissionRate = balance.getCommissionRate();
    
    if (commissionRate != null && commissionRate.compareTo(BigDecimal.ZERO) > 0) {
      BigDecimal fee = tradeAmount.multiply(commissionRate)
          .setScale(2, RoundingMode.HALF_UP);
      
      log.debug("수수료 계산: 거래금액={}, 수수료율={}%, 수수료={}", 
          tradeAmount, commissionRate.multiply(new BigDecimal("100")), fee);
      return fee;
    }
    
    // 기본 수수료율 사용
    log.debug("기본 수수료율 사용: 거래금액={}", tradeAmount);
    return tradeAmount.multiply(new BigDecimal("0.0025"))
        .setScale(2, RoundingMode.HALF_UP);
  }

}