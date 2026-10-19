package com.muscat.marketdata.domain.dto.admin;

import java.util.List;
import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;

// 일괄 비활성 요청
@Data
@NoArgsConstructor
@AllArgsConstructor
public class BulkDeleteRequest {
    private List<String> symbols;
}
