package com.muscat.gateway.controller;

import com.muscat.gateway.common.enums.response.GatewayResponse;
import java.net.URI;
import java.time.LocalDateTime;
import org.springframework.http.ProblemDetail;
import org.springframework.http.ResponseEntity;
import org.springframework.http.server.reactive.ServerHttpRequest;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

// Gateway fallback 컨트롤러(서비스 장애 · 타임아웃 때 503 ProblemDetail)
@RestController
@RequestMapping("/fallback")
public class FallbackController {

  private static final String PROBLEM_BASE_URL = "https://api.muscat.com/problems/";

  @RequestMapping
  public ResponseEntity<ProblemDetail> fallback(ServerHttpRequest request) {
    GatewayResponse code = GatewayResponse.SERVICE_UNAVAILABLE;
    ProblemDetail problem = ProblemDetail.forStatusAndDetail(code.getHttpStatus(), code.getMessage());
    problem.setType(URI.create(PROBLEM_BASE_URL + code.name().toLowerCase().replace("_", "-")));
    problem.setTitle(code.getHttpStatus().getReasonPhrase());
    problem.setInstance(URI.create(request.getPath().value()));
    problem.setProperty("errorCode", code.name());
    problem.setProperty("errorType", "EXTERNAL_SERVICE");
    problem.setProperty("timestamp", LocalDateTime.now());
    return ResponseEntity.status(code.getHttpStatus()).body(problem);
  }
}
