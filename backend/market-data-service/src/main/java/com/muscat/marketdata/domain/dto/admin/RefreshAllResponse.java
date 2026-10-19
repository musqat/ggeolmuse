package com.muscat.marketdata.domain.dto.admin;

import com.muscat.marketdata.domain.entity.AdminJobRun;
import java.time.LocalDateTime;
import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;

// 마지막 전체 재수집 기록
@Data
@NoArgsConstructor
@AllArgsConstructor
public class RefreshAllResponse {
    // 서울 시각
    private LocalDateTime lastRunAt;
    private int published;

    public static RefreshAllResponse of(AdminJobRun run) {
        return new RefreshAllResponse(run.getLastRunAt(), run.getPublished());
    }
}
