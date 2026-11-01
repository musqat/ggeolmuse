package com.muscat.jpatest;

import com.muscat.commonlib.config.QueryDslConfig;
import com.muscat.trade.domain.repository.TradeRepository;
import org.springframework.boot.autoconfigure.domain.EntityScan;
import org.springframework.context.annotation.Configuration;
import org.springframework.context.annotation.Import;
import org.springframework.data.jpa.repository.config.EnableJpaRepositories;

// 저장소 테스트용 JPA 설정(서비스 패키지 안에 두면 @SpringBootTest · @ComponentScan 에 걸려 밖에 둠)
@Configuration
@EntityScan(basePackages = "com.muscat.trade")
@EnableJpaRepositories(basePackageClasses = TradeRepository.class)
@Import(QueryDslConfig.class)
public class TradeJpaTestConfig {
}
