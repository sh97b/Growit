package com.growit.ai.service;

import java.io.IOException;
import java.io.InputStream;
import java.nio.charset.StandardCharsets;
import org.springframework.core.io.ClassPathResource;
import org.springframework.util.StreamUtils;

/**
 * 시작할 때 미리 읽지 않고 처음 쓸 때 읽는다. 배포 환경에서 파일이 빠져 있어도 서버 시작 자체는 막지 않고,
 * 그 요청만 실패하게 하기 위해서다.
 */
class PromptResourceLoader {

	private final String resourceName;
	private volatile String cached;

	PromptResourceLoader(String resourceName) {
		this.resourceName = resourceName;
	}

	String load() throws IOException {
		String value = cached;
		if (value != null) {
			return value;
		}
		try (InputStream in = new ClassPathResource(resourceName).getInputStream()) {
			value = StreamUtils.copyToString(in, StandardCharsets.UTF_8);
		}
		cached = value;
		return value;
	}
}
