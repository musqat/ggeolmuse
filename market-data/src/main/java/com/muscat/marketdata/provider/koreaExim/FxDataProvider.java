package com.muscat.marketdata.provider.koreaExim;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.muscat.marketdata.domain.dto.KoreaEximRateItem;
import com.muscat.marketdata.provider.MarketDataProvider;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Component;
import org.springframework.web.client.RestTemplate;
import org.springframework.web.util.UriComponentsBuilder;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.time.format.DateTimeFormatter;
import java.util.List;
import java.util.Objects;
import java.util.Optional;

@Slf4j
@Component
@RequiredArgsConstructor
public class FxDataProvider implements MarketDataProvider.FxSource {

    @Value("${marketdata.fx.koreaexim.url:https://oapi.koreaexim.go.kr/site/program/financial/exchangeJSON}")
    private String baseUrl;

    @Value("${marketdata.fx.koreaexim.authkey:}")
    private String authKey;

    private static final DateTimeFormatter DATE_FORMAT = DateTimeFormatter.ofPattern("yyyyMMdd");

    private final RestTemplate restTemplate;
    private final ObjectMapper objectMapper;

    @Override
    public Optional<BigDecimal> fetchUsdKrw(LocalDate date) {
        return fetchFxRate(date)
                .map(KoreaEximRateItem::parseRate);
    }

    public Optional<KoreaEximRateItem> fetchFxRate(LocalDate date) {
        try {
            String url = buildUrl(date);
            String response = restTemplate.getForObject(url, String.class);
            
            if (response == null || response.trim().isEmpty()) {
                log.warn("Korea Exim API - No data for date: {}", date);
                return Optional.empty();
            }

            List<KoreaEximRateItem> items = parseResponse(response);
            return items.stream()
                    .filter(Objects::nonNull)
                    .filter(KoreaEximRateItem::isUsd)
                    .filter(item -> item.parseRate() != null)
                    .findFirst();

        } catch (Exception e) {
            log.error("Korea Exim API - Failed to fetch rate for date: {} error: {}", date, e.getMessage());
            return Optional.empty();
        }
    }

    private String buildUrl(LocalDate date) {
        return UriComponentsBuilder.fromHttpUrl(baseUrl)
                .queryParam("searchdate", date.format(DATE_FORMAT))
                .queryParam("data", "AP01")
                .queryParam("authkey", authKey)
                .build(true)
                .toUriString();
    }

    private List<KoreaEximRateItem> parseResponse(String json) throws JsonProcessingException {
        JsonNode root = objectMapper.readTree(json);

        if (!root.isArray()) {
            log.warn("Korea Exim API - Unexpected response format");
            return List.of();
        }

        return objectMapper.convertValue(root,
                objectMapper.getTypeFactory().constructCollectionType(List.class, KoreaEximRateItem.class));
    }
}