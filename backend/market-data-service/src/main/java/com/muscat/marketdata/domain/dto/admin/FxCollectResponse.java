package com.muscat.marketdata.domain.dto.admin;

import com.muscat.marketdata.domain.service.FxCollectionService;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.List;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

// 환율 빈 날과 진행 중이거나 마지막 수집
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class FxCollectResponse {
    private int missingCount;
    // 최근 빈 날(최근부터 최대 열 개)
    private List<LocalDate> missingDates;
    private boolean running;
    // MISSING · ALL, 다시 띄운 뒤에는 비어 있음
    private String mode;
    // 서울 시각
    private LocalDateTime startedAt;
    private LocalDateTime finishedAt;
    private int total;
    private int processed;
    private int filled;
    private int failed;
    private LocalDate lastDate;
    private String stoppedReason;
    private String message;

    public static FxCollectResponse of(FxCollectionService.Status status) {
        FxCollectResponseBuilder builder = FxCollectResponse.builder()
            .missingCount(status.missingCount())
            .missingDates(status.missingDates())
            .running(status.running());
        FxCollectionService.Progress progress = status.progress();
        if (progress != null) {
            builder.mode(progress.getMode() != null ? progress.getMode().name() : null)
                .startedAt(progress.getStartedAt())
                .finishedAt(progress.getFinishedAt())
                .total(progress.getTotal())
                .processed(progress.getProcessed())
                .filled(progress.getFilled())
                .failed(progress.getFailed())
                .lastDate(progress.getLastDate())
                .stoppedReason(progress.getStoppedReason());
        }
        return builder.build();
    }

    public static FxCollectResponse message(String message) {
        return FxCollectResponse.builder().message(message).build();
    }
}
