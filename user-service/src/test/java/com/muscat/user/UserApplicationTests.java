package com.muscat.user;

import org.junit.jupiter.api.Test;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.test.context.ActiveProfiles;

@SpringBootTest(webEnvironment = SpringBootTest.WebEnvironment.NONE)
@ActiveProfiles("test")
class UserApplicationTests {
	@Test
	void contextLoads() {
		// 컨텍스트 로딩 테스트
	}
}
