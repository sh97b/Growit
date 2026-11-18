package com.growit.dot.service;

import com.growit.dot.dto.DotCell;
import com.growit.dot.dto.DotGrid;
import com.growit.dot.dto.DotPreset;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;
import java.util.TreeMap;

/**
 * 기본 그림(32x32)을 칸 수가 정확히 goalDays인 도트 그림으로 줄인다. 같은 입력이면 항상 같은 결과가 나온다.
 *
 * <p>가로 칸 수를 바꿔 가며 "절반 이상 차 있는 칸 수"가 목표에 가장 가까운 크기를 고른 뒤, 모자라거나 남는 칸은
 * 그림 가장자리에서 맞춘다. 많으면 덜 찬 칸부터 빼고, 모자라면 그림에 닿은 칸 중 더 찬 칸부터 더한다.
 */
public final class DotGridBuilder {

	private static final double EPS = 1e-9;

	private DotGridBuilder() {
	}

	public static DotGrid build(DotPreset preset, int goalDays) {
		int[][] source = parse(preset);
		int[] box = boundingBox(source);
		int minX = box[0];
		int minY = box[1];
		int boxWidth = box[2] - box[0] + 1;
		int boxHeight = box[3] - box[1] + 1;

		Sampled best = null;
		int maxWidth = Math.min(DotGridValidator.MAX_SIZE, boxWidth);
		for (int width = 2; width <= maxWidth; width++) {
			double cellSize = boxWidth / (double) width;
			int height = Math.max(1, (int) Math.round(boxHeight / cellSize));
			if (height > DotGridValidator.MAX_SIZE) {
				continue;
			}
			Sampled sampled = sample(source, minX, minY, boxWidth, boxHeight, width, height, cellSize, preset.palette().size());
			if (sampled.nonEmpty < goalDays) {
				continue;
			}
			if (best == null || Math.abs(sampled.half - goalDays) < Math.abs(best.half - goalDays)) {
				best = sampled;
			}
		}
		if (best == null) {
			throw new IllegalStateException("그림이 너무 작아 " + goalDays + "칸을 만들 수 없어요: " + preset.key());
		}
		return pick(best, preset, goalDays);
	}

	private static DotGrid pick(Sampled s, DotPreset preset, int goalDays) {
		List<int[]> candidates = new ArrayList<>();
		for (int y = 0; y < s.height; y++) {
			for (int x = 0; x < s.width; x++) {
				if (s.coverage[y][x] > EPS) {
					candidates.add(new int[] { x, y, support(s, x, y) });
				}
			}
		}
		double centerX = (s.width - 1) / 2.0;
		double centerY = (s.height - 1) / 2.0;
		candidates.sort(Comparator
				.comparingLong((int[] c) -> -Math.round(s.coverage[c[1]][c[0]] * 1_000_000))
				.thenComparingInt(c -> -c[2])
				.thenComparingDouble(c -> Math.pow(c[0] - centerX, 2) + Math.pow(c[1] - centerY, 2))
				.thenComparingInt(c -> c[1])
				.thenComparingInt(c -> c[0]));
		List<int[]> chosen = new ArrayList<>(candidates.subList(0, goalDays));
		chosen.sort(Comparator.comparingInt((int[] c) -> -c[1]).thenComparingInt(c -> c[0]));

		TreeMap<Integer, Integer> usedColors = new TreeMap<>();
		for (int[] c : chosen) {
			usedColors.putIfAbsent(s.color[c[1]][c[0]], 0);
		}
		List<String> palette = new ArrayList<>();
		for (Integer original : new ArrayList<>(usedColors.keySet())) {
			usedColors.put(original, palette.size());
			palette.add(preset.palette().get(original));
		}
		List<DotCell> cells = new ArrayList<>();
		for (int[] c : chosen) {
			cells.add(new DotCell(c[0], c[1], usedColors.get(s.color[c[1]][c[0]])));
		}
		return new DotGrid(s.width, s.height, palette, cells);
	}

	/** 주변 8칸 중 절반 이상 차 있는 칸 수. 그림 안쪽 칸을 가장자리에 달린 조각보다 먼저 남기려고 쓴다. */
	private static int support(Sampled s, int x, int y) {
		int count = 0;
		for (int dy = -1; dy <= 1; dy++) {
			for (int dx = -1; dx <= 1; dx++) {
				int nx = x + dx;
				int ny = y + dy;
				if ((dx != 0 || dy != 0) && nx >= 0 && ny >= 0 && nx < s.width && ny < s.height
						&& s.coverage[ny][nx] >= 0.5 - EPS) {
					count++;
				}
			}
		}
		return count;
	}

	private static Sampled sample(int[][] source, int minX, int minY, int boxWidth, int boxHeight, int width, int height,
			double cellSize, int colorCount) {
		double originX = minX - (width * cellSize - boxWidth) / 2.0;
		double originY = minY - (height * cellSize - boxHeight) / 2.0;
		double[][] coverage = new double[height][width];
		int[][] color = new int[height][width];
		int nonEmpty = 0;
		int half = 0;
		for (int cy = 0; cy < height; cy++) {
			for (int cx = 0; cx < width; cx++) {
				double x0 = originX + cx * cellSize;
				double y0 = originY + cy * cellSize;
				double[] colorArea = new double[colorCount];
				double filled = 0;
				for (int py = (int) Math.floor(Math.max(y0, minY)); py < Math.min(y0 + cellSize, minY + boxHeight); py++) {
					for (int px = (int) Math.floor(Math.max(x0, minX)); px < Math.min(x0 + cellSize, minX + boxWidth); px++) {
						if (source[py][px] < 0) {
							continue;
						}
						double overlapX = Math.min(px + 1, x0 + cellSize) - Math.max(px, x0);
						double overlapY = Math.min(py + 1, y0 + cellSize) - Math.max(py, y0);
						if (overlapX > 0 && overlapY > 0) {
							colorArea[source[py][px]] += overlapX * overlapY;
							filled += overlapX * overlapY;
						}
					}
				}
				coverage[cy][cx] = Math.min(1.0, filled / (cellSize * cellSize));
				int dominant = 0;
				for (int i = 1; i < colorCount; i++) {
					if (colorArea[i] > colorArea[dominant] + EPS) {
						dominant = i;
					}
				}
				color[cy][cx] = dominant;
				if (coverage[cy][cx] > EPS) {
					nonEmpty++;
				}
				if (coverage[cy][cx] >= 0.5 - EPS) {
					half++;
				}
			}
		}
		return new Sampled(width, height, coverage, color, nonEmpty, half);
	}

	private static int[][] parse(DotPreset preset) {
		if (preset.pixels().size() != preset.height()) {
			throw new IllegalArgumentException("그림 줄 수가 height와 달라요: " + preset.key());
		}
		int[][] source = new int[preset.height()][preset.width()];
		for (int y = 0; y < preset.height(); y++) {
			String row = preset.pixels().get(y);
			if (row.length() != preset.width()) {
				throw new IllegalArgumentException("그림 줄 길이가 width와 달라요: " + preset.key() + " " + y);
			}
			for (int x = 0; x < preset.width(); x++) {
				char ch = row.charAt(x);
				if (ch == '.') {
					source[y][x] = -1;
				} else if (ch >= '0' && ch - '0' < preset.palette().size()) {
					source[y][x] = ch - '0';
				} else {
					throw new IllegalArgumentException("그림 글자가 올바르지 않아요: " + preset.key() + " " + y + "," + x);
				}
			}
		}
		return source;
	}

	private static int[] boundingBox(int[][] source) {
		int minX = Integer.MAX_VALUE;
		int minY = Integer.MAX_VALUE;
		int maxX = -1;
		int maxY = -1;
		for (int y = 0; y < source.length; y++) {
			for (int x = 0; x < source[y].length; x++) {
				if (source[y][x] >= 0) {
					minX = Math.min(minX, x);
					minY = Math.min(minY, y);
					maxX = Math.max(maxX, x);
					maxY = Math.max(maxY, y);
				}
			}
		}
		if (maxX < 0) {
			throw new IllegalArgumentException("비어 있는 그림이에요");
		}
		return new int[] { minX, minY, maxX, maxY };
	}

	private record Sampled(int width, int height, double[][] coverage, int[][] color, int nonEmpty, int half) {
	}
}
