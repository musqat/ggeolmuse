package com.muscat.gateway.controller;

import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.time.LocalDateTime;
import java.util.Map;

// Gateway fallback 컨트롤러
// 서비스 장애 시 fallback 응답 제공
@RestController
@RequestMapping("/fallback")
public class FallbackController {

    // fallback 엔드포인트
    @GetMapping
    public ResponseEntity<Map<String, Object>> fallback() {
        Map<String, Object> response = Map.of(
            "error", "서비스 사용 불가",
            "message", "요청하신 서비스를 일시적으로 사용할 수 없습니다. 잠시 후 다시 시도해주세요.",
            "timestamp", LocalDateTime.now().toString(),
            "status", HttpStatus.SERVICE_UNAVAILABLE.value()
        );

        return ResponseEntity.status(HttpStatus.SERVICE_UNAVAILABLE).body(response);
    }
}
