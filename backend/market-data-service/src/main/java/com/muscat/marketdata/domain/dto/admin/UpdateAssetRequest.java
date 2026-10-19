package com.muscat.marketdata.domain.dto.admin;

import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;

// 종목 이름 수정 요청
@Data
@NoArgsConstructor
@AllArgsConstructor
public class UpdateAssetRequest {
    private String name;
}
