package com.muscat.marketdata.domain.model;

import tools.jackson.databind.JsonNode;

// Yahoo Finance 시계열 데이터
public record TimeSeriesData(
  JsonNode timestamps,
  JsonNode open,
  JsonNode high,
  JsonNode low,
  JsonNode close,
  JsonNode volume,
  JsonNode adjustedClose
) {
}
