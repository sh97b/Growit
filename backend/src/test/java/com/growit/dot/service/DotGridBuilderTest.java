package com.growit.dot.service;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.growit.dot.dto.DotCell;
import com.growit.dot.dto.DotGrid;
import com.growit.dot.dto.DotPreset;
import java.util.HashSet;
import java.util.List;
import java.util.Set;
import java.util.stream.IntStream;
import java.util.stream.Stream;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.Arguments;
import org.junit.jupiter.params.provider.MethodSource;

class DotGridBuilderTest {

	private static final DotPresetCatalog CATALOG = new DotPresetCatalog(new ObjectMapper());

	static Stream<Arguments> everyPresetAndEveryGoalDays() {
		return CATALOG.all().stream().flatMap(preset -> IntStream
				.rangeClosed(DotGridValidator.MIN_GOAL_DAYS, DotGridValidator.MAX_GOAL_DAYS)
				.mapToObj(days -> Arguments.of(preset.key(), days)));
	}

	@ParameterizedTest(name = "{0} / {1}칸")
	@MethodSource("everyPresetAndEveryGoalDays")
	void build_everyPresetAndGoalDays_producesExactlyThatManyValidCells(String key, int goalDays) {
		DotPreset preset = CATALOG.find(key).orElseThrow();

		DotGrid grid = DotGridBuilder.build(preset, goalDays);

		assertThat(grid.cells()).hasSize(goalDays);
		assertThat(grid.width()).isBetween(1, DotGridValidator.MAX_SIZE);
		assertThat(grid.height()).isBetween(1, DotGridValidator.MAX_SIZE);
		assertThat(grid.palette().size()).isBetween(1, DotGridValidator.MAX_COLORS);
		Set<String> positions = new HashSet<>();
		for (DotCell cell : grid.cells()) {
			assertThat(cell.x()).isBetween(0, grid.width() - 1);
			assertThat(cell.y()).isBetween(0, grid.height() - 1);
			assertThat(cell.color()).isBetween(0, grid.palette().size() - 1);
			assertThat(positions.add(cell.x() + "," + cell.y())).isTrue();
		}
		DotGridValidator.validate(goalDays, grid);
	}

	@Test
	void build_cellsAreInFillOrder_bottomRowFirstThenLeftToRight() {
		DotGrid grid = DotGridBuilder.build(CATALOG.find("book").orElseThrow(), 60);

		for (int i = 1; i < grid.cells().size(); i++) {
			DotCell before = grid.cells().get(i - 1);
			DotCell after = grid.cells().get(i);
			boolean ok = before.y() > after.y() || (before.y() == after.y() && before.x() < after.x());
			assertThat(ok).as("%d번째 칸 순서", i).isTrue();
		}
	}

	@Test
	void build_sameInput_givesSameGrid() {
		DotPreset preset = CATALOG.find("guitar").orElseThrow();

		assertThat(DotGridBuilder.build(preset, 77)).isEqualTo(DotGridBuilder.build(preset, 77));
	}

	@Test
	void build_paletteOnlyKeepsUsedColors() {
		DotGrid grid = DotGridBuilder.build(CATALOG.find("sprout-pot").orElseThrow(), 14);

		Set<Integer> used = new HashSet<>();
		grid.cells().forEach(cell -> used.add(cell.color()));
		assertThat(used).hasSize(grid.palette().size());
	}

	@Test
	void build_badPixelCharacter_throws() {
		DotPreset bad = new DotPreset("bad", "나쁜 그림", 2, 1, List.of("#000000"), List.of("0x"));

		assertThatThrownBy(() -> DotGridBuilder.build(bad, 14)).isInstanceOf(IllegalArgumentException.class);
	}

	@Test
	void build_pictureTooSmallForGoalDays_throws() {
		DotPreset tiny = new DotPreset("tiny", "작은 그림", 3, 1, List.of("#000000"), List.of("000"));

		assertThatThrownBy(() -> DotGridBuilder.build(tiny, 14)).isInstanceOf(IllegalStateException.class);
	}
}
