package com.muscat.jpatest;

import com.muscat.commonlib.config.QueryDslConfig;
import com.muscat.user.domain.account.repository.TradeSettlementRepository;
import org.springframework.boot.autoconfigure.domain.EntityScan;
import org.springframework.context.annotation.Configuration;
import org.springframework.context.annotation.Import;
import org.springframework.data.jpa.repository.config.EnableJpaRepositories;

/**
 * 레포지토리 테스트용 JPA 설정. 서비스 패키지 밖에 둔다.
 * 안에 두면 @SpringBootTest 가 설정을 찾다 이 클래스를 집고, UserApplication 의 @ComponentScan 에도 걸린다.
 */
@Configuration
@EntityScan(basePackages = "com.muscat.user")
@EnableJpaRepositories(basePackageClasses = TradeSettlementRepository.class)
@Import(QueryDslConfig.class)
public class UserJpaTestConfig {
}
