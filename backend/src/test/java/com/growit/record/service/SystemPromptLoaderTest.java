package com.growit.record.service;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import java.io.FileNotFoundException;
import org.junit.jupiter.api.Test;
import org.springframework.core.io.ClassPathResource;

class SystemPromptLoaderTest {

	/** 빌드된 jar(클래스패스)에 프롬프트 파일이 실제로 포함됐는지 확인한다(배포 환경 누락을 조기에 잡기 위해). */
	@Test
	void promptResource_existsOnClasspath() {
		assertThat(new ClassPathResource("record-analysis-system.txt").exists()).isTrue();
	}

	@Test
	void load_existingResource_returnsContent() throws Exception {
		SystemPromptLoader loader = new SystemPromptLoader("record-analysis-system.txt");

		String content = loader.load();

		assertThat(content).isNotBlank();
	}

	@Test
	void load_missingResource_throwsIOException() {
		SystemPromptLoader loader = new SystemPromptLoader("does-not-exist.txt");

		assertThatThrownBy(loader::load).isInstanceOf(FileNotFoundException.class);
	}
}
