package com.muscat.trade.domain.service;

import com.muscat.trade.domain.dto.response.DividendResponseDto;
import java.util.List;

public interface DividendService {

  // 세후 몫이 있는 배당(배당락일 최신순)
  List<DividendResponseDto> getUserDividends(String userId);
}
