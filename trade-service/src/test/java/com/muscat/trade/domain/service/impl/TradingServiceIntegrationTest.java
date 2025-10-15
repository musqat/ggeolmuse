package com.muscat.trade.domain.service.impl;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.BDDMockito.given;
import static org.mockito.Mockito.doNothing;

import com.muscat.trade.common.enums.type.PriceType;
import com.muscat.trade.common.enums.type.TradeType;
import com.muscat.trade.common.exception.NotEnoughHoldingsException;
import com.muscat.trade.domain.dto.response.TradeResponseDto;
import com.muscat.trade.domain.entity.Holdings;
import com.muscat.trade.domain.entity.Trade;
import com.muscat.trade.domain.repository.HoldingsRepository;
import com.muscat.trade.domain.repository.TradeRepository;
import com.muscat.trade.domain.service.MarketDataService;
import com.muscat.trade.domain.service.TradingService;
import com.muscat.trade.infra.client.UserServiceClient;
import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.List;
import java.util.Optional;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.test.mock.mockito.MockBean;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.transaction.annotation.Transactional;

@SpringBootTest(webEnvironment = SpringBootTest.WebEnvironment.MOCK)
@ActiveProfiles("test")
@Transactional
@DisplayName("TradingService 통합 테스트")
class TradingServiceIntegrationTest {

  @Autowired
  private TradingService tradingService;

  @Autowired
  private TradeRepository tradeRepository;

  @Autowired
  private HoldingsRepository holdingsRepository;

  @MockBean
  private UserServiceClient userServiceClient;

  @MockBean
  private MarketDataService marketDataService;

  private String userId;
  private Long accountId;
  private String symbol;

  @BeforeEach
  void setUp() {
    userId = "test-user";
    accountId = 999L;
    symbol = "INTG";

    // 외부 서비스 Mock 설정 - Void 메서드는 doNothing() 사용
    doNothing().when(userServiceClient).updateTradeBalance(
      any(Long.class), any(BigDecimal.class), anyString(), anyString());

    given(marketDataService.determineTradePrice(anyString(), any(LocalDate.class),
      any(PriceType.class), any()))
      .willReturn(new BigDecimal("100.00"));
  }

  @AfterEach
  void tearDown() {
    // 테스트 데이터 정리
    tradeRepository.deleteAll();
    holdingsRepository.deleteAll();
  }

  @Test
  @DisplayName("매수-매도 전체 플로우가 정상 동작한다")
  void buyAndSellFlow_Success() {
    // given
    BigDecimal buyQuantity = new BigDecimal("10");
    BigDecimal sellQuantity = new BigDecimal("5");
    LocalDate tradeDate = LocalDate.now();

    // when - 1. 매수
    TradeResponseDto buyResult = tradingService.buyStock(
      userId, accountId, symbol, buyQuantity, tradeDate, PriceType.CLOSE, null);

    // then - 매수 확인
    assertThat(buyResult).isNotNull();
    assertThat(buyResult.getTradeType()).isEqualTo(TradeType.BUY);
    assertThat(buyResult.getQuantity()).isEqualByComparingTo(buyQuantity);

    // Holdings 확인
    Optional<Holdings> holdingsAfterBuy = holdingsRepository
      .findByUserIdAndAccountIdAndSymbolWithLock(userId, accountId, symbol);
    assertThat(holdingsAfterBuy).isPresent();
    assertThat(holdingsAfterBuy.get().getTotalQuantity()).isEqualByComparingTo(buyQuantity);

    // when - 2. 부분 매도
    TradeResponseDto sellResult = tradingService.sellStock(
      userId, accountId, symbol, sellQuantity, tradeDate, PriceType.CLOSE, null);

    // then - 매도 확인
    assertThat(sellResult).isNotNull();
    assertThat(sellResult.getTradeType()).isEqualTo(TradeType.SELL);
    assertThat(sellResult.getQuantity()).isEqualByComparingTo(sellQuantity);

    // Holdings 확인 - 잔여 수량
    Optional<Holdings> holdingsAfterSell = holdingsRepository
      .findByUserIdAndAccountIdAndSymbolWithLock(userId, accountId, symbol);
    assertThat(holdingsAfterSell).isPresent();
    assertThat(holdingsAfterSell.get().getTotalQuantity())
      .isEqualByComparingTo(buyQuantity.subtract(sellQuantity));

    // Trade 기록 확인
    List<Trade> trades = tradeRepository.findByUserIdAndSymbolOrderByExecutedAtDesc(userId, symbol);
    assertThat(trades).hasSize(2);
    assertThat(trades.get(0).getTradeType()).isEqualTo(TradeType.SELL);
    assertThat(trades.get(1).getTradeType()).isEqualTo(TradeType.BUY);
  }

  @Test
  @DisplayName("전량 매도 시 Holdings가 삭제된다")
  void sellAll_DeletesHoldings() {
    // given
    BigDecimal quantity = new BigDecimal("10");
    LocalDate tradeDate = LocalDate.now();

    // 먼저 매수
    tradingService.buyStock(userId, accountId, symbol, quantity, tradeDate, PriceType.CLOSE, null);

    // when - 전량 매도
    tradingService.sellStock(userId, accountId, symbol, quantity, tradeDate, PriceType.CLOSE, null);

    // then - Holdings 삭제 확인
    Optional<Holdings> holdings = holdingsRepository
      .findByUserIdAndAccountIdAndSymbolWithLock(userId, accountId, symbol);
    assertThat(holdings).isEmpty();

    // Trade 기록은 남아있어야 함
    List<Trade> trades = tradeRepository.findByUserIdAndSymbolOrderByExecutedAtDesc(userId, symbol);
    assertThat(trades).hasSize(2);
  }

  @Test
  @DisplayName("여러 번 매수 시 평균 단가가 올바르게 계산된다")
  void multipleBuys_CalculatesAveragePriceCorrectly() {
    // given
    BigDecimal firstQuantity = new BigDecimal("10");
    BigDecimal firstPrice = new BigDecimal("100.00");
    BigDecimal secondQuantity = new BigDecimal("5");
    BigDecimal secondPrice = new BigDecimal("120.00");
    LocalDate tradeDate = LocalDate.now();

    given(marketDataService.determineTradePrice(symbol, tradeDate, PriceType.CLOSE, null))
      .willReturn(firstPrice, secondPrice);

    // when - 첫 번째 매수
    tradingService.buyStock(userId, accountId, symbol, firstQuantity, tradeDate, PriceType.CLOSE,
      null);

    // then - 첫 매수 후 평균 단가 확인
    Optional<Holdings> holdingsAfterFirst = holdingsRepository
      .findByUserIdAndAccountIdAndSymbolWithLock(userId, accountId, symbol);
    assertThat(holdingsAfterFirst).isPresent();
    assertThat(holdingsAfterFirst.get().getAvgPurchasePrice())
      .isEqualByComparingTo(firstPrice);

    // when - 두 번째 매수
    tradingService.buyStock(userId, accountId, symbol, secondQuantity, tradeDate, PriceType.CLOSE,
      null);

    // then - 평균 단가 계산 확인
    // 평균 = (10 * 100 + 5 * 120) / 15 = 1600 / 15 = 106.67
    Optional<Holdings> holdingsAfterSecond = holdingsRepository
      .findByUserIdAndAccountIdAndSymbolWithLock(userId, accountId, symbol);
    assertThat(holdingsAfterSecond).isPresent();
    assertThat(holdingsAfterSecond.get().getTotalQuantity())
      .isEqualByComparingTo(new BigDecimal("15"));
    assertThat(holdingsAfterSecond.get().getAvgPurchasePrice())
      .isGreaterThan(firstPrice)
      .isLessThan(secondPrice);
  }

  @Test
  @DisplayName("매수 없이 매도 시도 시 예외가 발생한다")
  void sellWithoutBuy_ThrowsException() {
    // given
    BigDecimal quantity = new BigDecimal("10");
    LocalDate tradeDate = LocalDate.now();

    // when & then
    assertThatThrownBy(() ->
      tradingService.sellStock(userId, accountId, symbol, quantity, tradeDate, PriceType.CLOSE,
        null)
    ).isInstanceOf(NotEnoughHoldingsException.class);

    // Holdings와 Trade가 생성되지 않았는지 확인
    Optional<Holdings> holdings = holdingsRepository
      .findByUserIdAndAccountIdAndSymbolWithLock(userId, accountId, symbol);
    assertThat(holdings).isEmpty();

    List<Trade> trades = tradeRepository.findByUserIdAndSymbolOrderByExecutedAtDesc(userId, symbol);
    assertThat(trades).isEmpty();
  }

  @Test
  @DisplayName("동일 사용자가 여러 계좌에서 같은 종목을 거래할 수 있다")
  void multipleAccounts_SameSymbol_WorksIndependently() {
    // given
    Long account1 = 100L;
    Long account2 = 200L;
    BigDecimal quantity = new BigDecimal("10");
    LocalDate tradeDate = LocalDate.now();

    // when - 두 계좌에서 각각 매수
    tradingService.buyStock(userId, account1, symbol, quantity, tradeDate, PriceType.CLOSE, null);
    tradingService.buyStock(userId, account2, symbol, quantity, tradeDate, PriceType.CLOSE, null);

    // then - 각 계좌별로 독립적인 Holdings 생성 확인
    Optional<Holdings> holdings1 = holdingsRepository
      .findByUserIdAndAccountIdAndSymbolWithLock(userId, account1, symbol);
    Optional<Holdings> holdings2 = holdingsRepository
      .findByUserIdAndAccountIdAndSymbolWithLock(userId, account2, symbol);

    assertThat(holdings1).isPresent();
    assertThat(holdings2).isPresent();
    assertThat(holdings1.get().getHoldingId()).isNotEqualTo(holdings2.get().getHoldingId());
    assertThat(holdings1.get().getTotalQuantity()).isEqualByComparingTo(quantity);
    assertThat(holdings2.get().getTotalQuantity()).isEqualByComparingTo(quantity);
  }

  @Test
  @DisplayName("거래 가능 여부 검증이 실제 DB 상태를 반영한다")
  void canTrade_ReflectsActualDbState() {
    // given
    BigDecimal quantity = new BigDecimal("10");
    LocalDate tradeDate = LocalDate.now();

    // when - 초기 상태: 매도 불가
    boolean canSellBefore = tradingService.canSellStock(userId, accountId, symbol, quantity);

    // 매수 실행
    tradingService.buyStock(userId, accountId, symbol, quantity, tradeDate, PriceType.CLOSE, null);

    // 매수 후: 매도 가능
    boolean canSellAfter = tradingService.canSellStock(userId, accountId, symbol, quantity);

    // then
    assertThat(canSellBefore).isFalse();
    assertThat(canSellAfter).isTrue();
  }
}
