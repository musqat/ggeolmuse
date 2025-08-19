package com.muscat.marketdata.provider.koreaExim;


import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.muscat.marketdata.domain.dto.FxRateDto.KoreaEximRateItem;
import jakarta.annotation.PostConstruct;
import java.math.BigDecimal;
import java.time.LocalDate;
import java.time.format.DateTimeFormatter;
import java.util.List;
import java.util.Objects;
import java.util.Optional;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Component;
import org.springframework.web.client.RestTemplate;
import org.springframework.web.util.UriComponentsBuilder;

@Slf4j
@Component
@RequiredArgsConstructor
public class FxDataProvider {

  @Value("${marketdata.fx.koreaexim.url:https://oapi.koreaexim.go.kr/site/program/financial/exchangeJSON}")
  private String baseUrl;

  @Value("${marketdata.fx.koreaexim.authkey:}")
  private String authKey;

  private static final DateTimeFormatter DATE_FORMAT = DateTimeFormatter.ofPattern("yyyyMMdd");

  private final RestTemplate restTemplate;
  private final ObjectMapper objectMapper;

  public Optional<BigDecimal> fetchUsdKrw(LocalDate date) {
    return fetchFxRate(date)
        .map(item -> KoreaEximRateItem.parseAmount(item.getDealBasR()));
  }

  public Optional<KoreaEximRateItem> fetchFxRate(LocalDate date) {
    try {
      String url = buildUrl(date);

      String response = restTemplate.getForObject(url, String.class);
      log.debug("[FxProvider] {} 응답: {}", date, response);

      if (response == null || response.trim().isEmpty()) {
        log.warn("[FxProvider] {} 일자 결과 없음", date);
        return Optional.empty();
      }

      List<KoreaEximRateItem> items = parseResponse(response);
      log.debug("[FxProvider] {} 파싱 결과: {}개 아이템", date, items.size());

      Optional<KoreaEximRateItem> result = items.stream()
          .filter(Objects::nonNull)
          .filter(KoreaEximRateItem::isUsd)
          .filter(item -> KoreaEximRateItem.parseAmount(item.getDealBasR()) != null)
          .findFirst();

      log.debug("[FxProvider] {} USD 필터링 결과: {}", date, result.isPresent() ? "발견" : "없음");

      return result;

    } catch (Exception e) {
      log.error("[FxProvider] 조회 실패 date={} err={}", date, e.getMessage());
      return Optional.empty();
    }
  }


  private String buildUrl(LocalDate date) {
    String url = UriComponentsBuilder.fromHttpUrl(baseUrl)
        .queryParam("searchdate", date.format(DATE_FORMAT))
        .queryParam("data", "AP01")
        .queryParam("authkey", authKey)
        .build(true)
        .toUriString();

    return url;
  }

  private List<KoreaEximRateItem> parseResponse(String json) throws JsonProcessingException {
    JsonNode root = objectMapper.readTree(json);

    if (!root.isArray()) {
      log.warn("[FxProvider] 예상치 못한 응답 형태");
      return List.of();
    }

    return objectMapper.convertValue(root,
        objectMapper.getTypeFactory().constructCollectionType(List.class, KoreaEximRateItem.class));
  }
}