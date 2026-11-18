package com.growit.dot.dto;

import java.util.List;

/** pixels의 각 글자는 팔레트 번호(0~7)이고 '.'은 투명이다. */
public record DotPreset(String key, String label, int width, int height, List<String> palette, List<String> pixels) {
}
