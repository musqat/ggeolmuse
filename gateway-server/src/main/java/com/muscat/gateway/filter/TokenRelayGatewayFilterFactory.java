package com.muscat.gateway.filter;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.cloud.gateway.filter.GatewayFilter;
import org.springframework.cloud.gateway.filter.factory.AbstractGatewayFilterFactory;
import org.springframework.http.HttpHeaders;
import org.springframework.security.core.context.ReactiveSecurityContextHolder;
import org.springframework.security.core.context.SecurityContext;
import org.springframework.security.oauth2.server.resource.authentication.JwtAuthenticationToken;
import org.springframework.stereotype.Component;
import reactor.core.publisher.Mono;

@Component
public class TokenRelayGatewayFilterFactory extends AbstractGatewayFilterFactory<Object> {

    private static final Logger log = LoggerFactory.getLogger(TokenRelayGatewayFilterFactory.class);

    public TokenRelayGatewayFilterFactory() {
        super(Object.class);
    }

    @Override
    public GatewayFilter apply(Object config) {
        return (exchange, chain) -> ReactiveSecurityContextHolder.getContext()
            .map(SecurityContext::getAuthentication)
            .doOnNext(auth -> log.info("TokenRelay - Authentication type: {}", auth != null ? auth.getClass().getSimpleName() : "null"))
            .filter(auth -> auth instanceof JwtAuthenticationToken)
            .cast(JwtAuthenticationToken.class)
            .map(auth -> {
                String tokenValue = auth.getToken().getTokenValue();
                log.info("TokenRelay - Forwarding JWT token to downstream service");
                return exchange.mutate()
                    .request(r -> r.header(HttpHeaders.AUTHORIZATION, "Bearer " + tokenValue))
                    .build();
            })
            .defaultIfEmpty(exchange)
            .doOnNext(ex -> {
                if (!ex.getRequest().getHeaders().containsKey(HttpHeaders.AUTHORIZATION)) {
                    log.warn("TokenRelay - No JWT token found in SecurityContext, request will be sent without Authorization header");
                }
            })
            .flatMap(chain::filter);
    }
}
