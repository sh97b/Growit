package com.growit.dot.service;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.growit.dot.dto.DotPreset;
import java.io.IOException;
import java.io.InputStream;
import java.io.UncheckedIOException;
import java.util.List;
import java.util.Optional;
import org.springframework.core.io.ClassPathResource;

/** 기본 그림 5개를 classpath의 dot-presets/*.json에서 읽는다. 목록 순서가 화면에 보이는 순서다. */
public class DotPresetCatalog {

	static final List<String> KEYS = List.of("sprout-pot", "guitar", "dumbbell", "book", "laptop");

	private final List<DotPreset> presets;

	public DotPresetCatalog(ObjectMapper objectMapper) {
		this.presets = KEYS.stream().map(key -> load(objectMapper, key)).toList();
	}

	public List<DotPreset> all() {
		return presets;
	}

	public Optional<DotPreset> find(String key) {
		return presets.stream().filter(preset -> preset.key().equals(key)).findFirst();
	}

	private static DotPreset load(ObjectMapper objectMapper, String key) {
		try (InputStream in = new ClassPathResource("dot-presets/" + key + ".json").getInputStream()) {
			return objectMapper.readValue(in, DotPreset.class);
		} catch (IOException e) {
			throw new UncheckedIOException("기본 그림을 읽지 못했어요: " + key, e);
		}
	}
}
