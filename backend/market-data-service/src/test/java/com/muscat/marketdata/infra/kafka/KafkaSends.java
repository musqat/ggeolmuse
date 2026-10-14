package com.muscat.marketdata.infra.kafka;

import java.util.concurrent.CompletableFuture;
import org.apache.kafka.clients.producer.RecordMetadata;
import org.apache.kafka.common.TopicPartition;
import org.springframework.kafka.support.SendResult;

// 프로듀서 테스트에서 KafkaTemplate.send 가 돌려줄 결과
final class KafkaSends {

  private KafkaSends() {
  }

  static <V> CompletableFuture<SendResult<String, V>> sent(String topic) {
    RecordMetadata metadata = new RecordMetadata(new TopicPartition(topic, 0), 0L, 0, 0L, 0, 0);
    return CompletableFuture.completedFuture(new SendResult<>(null, metadata));
  }

  static <V> CompletableFuture<SendResult<String, V>> failed() {
    return CompletableFuture.failedFuture(new RuntimeException("broker down"));
  }
}
