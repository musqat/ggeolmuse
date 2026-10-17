package com.muscat.trade.infra.client.dto;

import java.util.List;

// user 배당 정산 한 쪽
public record DividendSettlementPageDto(List<DividendSettlementDto> content, boolean last) {
}
