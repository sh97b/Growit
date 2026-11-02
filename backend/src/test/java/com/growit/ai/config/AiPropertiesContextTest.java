package com.growit.ai.config;

import static org.assertj.core.api.Assertions.assertThat;

import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;

@SpringBootTest
class AiPropertiesContextTest {

	@Autowired
	private AiProperties aiProperties;

	@Test
	void serverStartsWithAiDisabledWhenKeyAndModelAreMissing() {
		assertThat(aiProperties.isEnabled()).isFalse();
	}
}
