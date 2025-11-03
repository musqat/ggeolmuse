package com.muscat.marketdata.datasource.yf.client;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.http.HttpHeaders;
import org.springframework.http.MediaType;
import org.springframework.http.RequestEntity;
import org.springframework.http.ResponseEntity;
import org.springframework.stereotype.Component;
import org.springframework.web.client.RestTemplate;

import java.net.URI;

/**
 * NASDAQ Screener API 클라이언트
 * NASDAQ 공개 API를 통해 실시간 종목 리스트를 가져옵니다.
 */
@Slf4j
@Component
@RequiredArgsConstructor
public class NasdaqScreenerClient {

    private static final String BASE_URL = "https://api.nasdaq.com/api/screener/stocks";
    private static final String USER_AGENT = "Mozilla/5.0 (Windows NT 10.0; Win64; x64) AppleWebKit/537.36";

    private final RestTemplate restTemplate;

    /**
     * NASDAQ 종목 리스트 조회
     *
     */
    public String getStocks(String exchange, String marketCap, int limit, int offset) {
        String url = buildUrl(exchange, marketCap, limit, offset);
        log.info("NASDAQ Screener API 호출: exchange={}, marketCap={}, limit={}, offset={}, url={}",
                 exchange, marketCap, limit, offset, url);

        try {
            HttpHeaders headers = createHeaders();
            RequestEntity<Void> request = RequestEntity
                    .get(URI.create(url))
                    .headers(headers)
                    .build();

            ResponseEntity<String> response = restTemplate.exchange(request, String.class);
            String body = response.getBody();

            if (body == null || body.isEmpty()) {
                log.warn("NASDAQ Screener API 빈 응답: exchange={}", exchange);
                return "{}";
            }

            log.info("NASDAQ Screener API 응답 수신 성공: exchange={}, size={} bytes",
                     exchange, body.length());
            return body;

        } catch (Exception e) {
            log.error("NASDAQ Screener API 호출 실패: exchange={}, error={}",
                     exchange, e.getMessage(), e);
            return "{}";
        }
    }

    /**
     * 전체 종목 조회 (페이징 처리)
     *
     * @param exchange 거래소
     * @param marketCap 시가총액 필터 (mega, large, mid, small, micro)
     * @return 모든 종목의 JSON 응답 (배열)
     */
    public String getAllStocks(String exchange, String marketCap) {
        log.info("NASDAQ Screener 전체 종목 조회 시작: exchange={}, marketCap={}", exchange, marketCap);

        // NASDAQ API는 limit을 크게 설정하면 한 번에 가져올 수 있음
        return getStocks(exchange, marketCap, 10000, 0);
    }

    private String buildUrl(String exchange, String marketCap, int limit, int offset) {
        StringBuilder url = new StringBuilder(BASE_URL);
        url.append("?tableonly=true");
        url.append("&limit=").append(limit);
        url.append("&offset=").append(offset);
        url.append("&download=true");

        if (exchange != null && !exchange.isEmpty()) {
            url.append("&exchange=").append(exchange.toUpperCase());
        }

        // 시가총액 필터 추가 (예: mega,large)
        if (marketCap != null && !marketCap.isEmpty()) {
            url.append("&marketcap=").append(marketCap.toLowerCase());
        }

        return url.toString();
    }

    private HttpHeaders createHeaders() {
        HttpHeaders headers = new HttpHeaders();
        headers.set(HttpHeaders.ACCEPT, MediaType.APPLICATION_JSON_VALUE);
        headers.set(HttpHeaders.USER_AGENT, USER_AGENT);
        headers.set(HttpHeaders.ACCEPT_ENCODING, "gzip, deflate, br");
        headers.set(HttpHeaders.ACCEPT_LANGUAGE, "en-US,en;q=0.9");
        return headers;
    }
}
