package com.growit.dot.service;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.growit.common.exception.ApiException;
import com.growit.common.exception.ErrorCode;
import com.growit.dot.dto.DotCell;
import com.growit.dot.dto.DotGrid;
import java.util.ArrayList;
import java.util.List;
import java.util.stream.IntStream;
import org.junit.jupiter.api.Test;

class DotGridValidatorTest {

	private static DotGrid gridOf(int count, int width, int height, List<String> palette) {
		List<DotCell> cells = new ArrayList<>();
		IntStream.range(0, count).forEach(i -> cells.add(new DotCell(i % width, i / width, 0)));
		return new DotGrid(width, height, palette, cells);
	}

	private static void assertInvalid(int goalDays, DotGrid grid) {
		assertThatThrownBy(() -> DotGridValidator.validate(goalDays, grid))
				.isInstanceOfSatisfying(ApiException.class,
						e -> assertThat(e.getCode()).isEqualTo(ErrorCode.VALIDATION_ERROR));
	}

	@Test
	void validate_goalDaysOutOfRange_rejected() {
		assertInvalid(13, gridOf(13, 5, 5, List.of("#112233")));
		assertInvalid(201, gridOf(201, 20, 20, List.of("#112233")));
	}

	@Test
	void validate_rangeEdges_accepted() {
		DotGridValidator.validate(14, gridOf(14, 5, 5, List.of("#112233")));
		DotGridValidator.validate(200, gridOf(200, 20, 20, List.of("#112233")));
	}

	@Test
	void validate_cellCountDifferentFromGoalDays_rejected() {
		assertInvalid(30, gridOf(29, 10, 10, List.of("#112233")));
		assertInvalid(30, gridOf(31, 10, 10, List.of("#112233")));
	}

	@Test
	void validate_sizeOverLimit_rejected() {
		assertInvalid(14, gridOf(14, 41, 3, List.of("#112233")));
		assertInvalid(14, gridOf(14, 3, 41, List.of("#112233")));
		assertInvalid(14, new DotGrid(0, 3, List.of("#112233"), gridOf(14, 5, 5, List.of("#112233")).cells()));
	}

	@Test
	void validate_colorCountOverLimit_rejected() {
		List<String> nine = IntStream.range(0, 9).mapToObj(i -> "#11223" + i).toList();
		assertInvalid(14, gridOf(14, 5, 5, nine));
		assertInvalid(14, gridOf(14, 5, 5, List.of()));
	}

	@Test
	void validate_badColorString_rejected() {
		assertInvalid(14, gridOf(14, 5, 5, List.of("red")));
	}

	@Test
	void validate_cellOutsideGridOrBadColorIndex_rejected() {
		List<DotCell> cells = new ArrayList<>(gridOf(14, 5, 5, List.of("#112233")).cells());
		cells.set(0, new DotCell(5, 0, 0));
		assertInvalid(14, new DotGrid(5, 5, List.of("#112233"), cells));

		cells.set(0, new DotCell(0, 0, 1));
		assertInvalid(14, new DotGrid(5, 5, List.of("#112233"), cells));

		cells.set(0, new DotCell(-1, 0, 0));
		assertInvalid(14, new DotGrid(5, 5, List.of("#112233"), cells));
	}

	@Test
	void validate_duplicatedPosition_rejected() {
		List<DotCell> cells = new ArrayList<>(gridOf(14, 5, 5, List.of("#112233")).cells());
		cells.set(1, cells.get(0));
		assertInvalid(14, new DotGrid(5, 5, List.of("#112233"), cells));
	}

	@Test
	void catalog_hasFivePresetsInOrderWithinLimits() {
		DotPresetCatalog catalog = new DotPresetCatalog(new ObjectMapper());

		assertThat(catalog.all()).extracting("key")
				.containsExactly("sprout-pot", "guitar", "dumbbell", "book", "laptop");
		catalog.all().forEach(preset -> {
			assertThat(preset.width()).isEqualTo(32);
			assertThat(preset.height()).isEqualTo(32);
			assertThat(preset.pixels()).hasSize(32).allSatisfy(row -> assertThat(row).hasSize(32));
			assertThat(preset.palette().size()).isBetween(1, DotGridValidator.MAX_COLORS);
		});
		assertThat(catalog.find("nope")).isEmpty();
	}
}
