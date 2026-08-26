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
        log.info("NASDAQ Screener 전체 종목 조회 시작: exchange={}", exchange);

        // marketcap 콤마구분 필터(mega,large,mid,small)는 NASDAQ이 빈 결과를 반환함.
        // 시총 매칭엔 필터 불필요 → 전 종목 조회 후 보유 종목만 매칭한다.
        return getStocks(exchange, null, 10000, 0);
    }


    private String buildUrl(String exchange, String marketCap, int limit, int offset) {
        // download=true 는 marketcap 필터와 조합 시 빈 결과(rows=null)를 반환함.
        // data.table.rows 구조로 marketCap 포함 정상 응답.
        StringBuilder url = new StringBuilder(BASE_URL);
        url.append("?tableonly=true");
        url.append("&limit=").append(limit);
        url.append("&offset=").append(offset);

        if (exchange != null && !exchange.isEmpty()) {
            url.append("&exchange=").append(exchange.toUpperCase());
        }

        // 시가총액 필터 추가 (예: mega,large)
        if (marketCap != null && !marketCap.isEmpty()) {
            //   mega        47건
            //   large      614건
            //   mega|large 661건   (합과 일치)
            //   mega,large   0건
            //
            // 파이프는 URI.create 가 거부하는 문자라 %7C 로 인코딩해서 넣는다.
            //
            // 참고 — getAllStocks 는 marketCap 을 null 로 넘겨 필터를 쓰지 않는다.
            String normalized = marketCap.toLowerCase()
                .replace(",", "%7C")
                .replace("|", "%7C");
            url.append("&marketcap=").append(normalized);
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
