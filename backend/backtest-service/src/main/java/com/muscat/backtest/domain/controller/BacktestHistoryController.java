package com.muscat.backtest.domain.controller;

import com.muscat.backtest.domain.dto.response.BacktestHistoryDto;
import com.muscat.backtest.domain.service.BacktestHistoryService;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.Parameter;
import io.swagger.v3.oas.annotations.tags.Tag;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Pageable;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

@RestController
@RequestMapping("/api/backtest/history")
@RequiredArgsConstructor
@Slf4j
@Tag(name = "백테스트 히스토리", description = "백테스트 히스토리 조회 API")
public class BacktestHistoryController {

    private final BacktestHistoryService backtestHistoryService;

    @GetMapping
    @Operation(summary = "사용자 백테스트 히스토리 조회", description = "로그인한 사용자의 백테스트 실행 히스토리를 페이징하여 조회합니다.")
    public ResponseEntity<Page<BacktestHistoryDto>> getUserBacktestHistory(
        @Parameter(description = "사용자 ID (이메일)", required = true)
        @RequestParam(value = "userId") String userId,
        @Parameter(description = "페이지 번호 (0부터 시작)", example = "0")
        @RequestParam(value = "page", defaultValue = "0") int page,
        @Parameter(description = "페이지 크기", example = "20")
        @RequestParam(value = "size", defaultValue = "20") int size
    ) {
        log.debug("백테스트 히스토리 조회 요청: userId={}, page={}, size={}", userId, page, size);

        Pageable pageable = PageRequest.of(page, size);
        Page<BacktestHistoryDto> historyPage = backtestHistoryService.getUserBacktestHistory(userId, pageable);

        return ResponseEntity.ok(historyPage);
    }
}
