package com.smarteye;

import org.junit.jupiter.api.Test;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.test.context.ActiveProfiles;

@SpringBootTest(webEnvironment = SpringBootTest.WebEnvironment.RANDOM_PORT)
@ActiveProfiles("test")
class SmartEyeApplicationTests {

	@Test
	void contextLoads() {
		// Application context loads successfully with full integration
	}

}