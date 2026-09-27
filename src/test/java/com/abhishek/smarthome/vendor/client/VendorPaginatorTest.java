package com.abhishek.smarthome.vendor.client;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import java.util.ArrayList;
import java.util.List;
import org.junit.jupiter.api.Test;

class VendorPaginatorTest {

	/** Three pages: [1,2] -> "p2" -> [3,4] -> "p3" -> [5]. Records the cursors it was called with. */
	private final List<String> calls = new ArrayList<>();

	private final PageFetcher<Integer> threePages = cursor -> {
		calls.add(String.valueOf(cursor));
		if (cursor == null) {
			return VendorPage.of(List.of(1, 2), "p2");
		}
		return switch (cursor) {
			case "p2" -> VendorPage.of(List.of(3, 4), "p3");
			case "p3" -> VendorPage.last(List.of(5));
			default -> throw new AssertionError("unexpected cursor " + cursor);
		};
	};

	@Test
	void shouldFollowCursorsUntilLastPage() {
		assertThat(VendorPaginator.fetchAll(threePages)).containsExactly(1, 2, 3, 4, 5);
		assertThat(calls).containsExactly("null", "p2", "p3");
	}

	@Test
	void shouldHandleSinglePage() {
		assertThat(VendorPaginator.fetchAll(cursor -> VendorPage.last(List.of("only")))).containsExactly("only");
	}

	@Test
	void shouldFetchLazily_whenStreamed() {
		assertThat(VendorPaginator.stream(threePages, 10).limit(3).toList()).containsExactly(1, 2, 3);
		assertThat(calls).containsExactly("null", "p2");
	}

	@Test
	void shouldStop_whenMaxPagesExceeded() {
		assertThatThrownBy(() -> VendorPaginator.fetchAll(threePages, 2))
				.isInstanceOf(VendorPaginationException.class)
				.hasMessageContaining("Stopped after 2 pages");
	}

	@Test
	void shouldStop_whenVendorRepeatsACursor() {
		PageFetcher<Integer> stuck = cursor -> VendorPage.of(List.of(1), "same");

		assertThatThrownBy(() -> VendorPaginator.fetchAll(stuck))
				.isInstanceOf(VendorPaginationException.class)
				.hasMessageContaining("'same' twice");
	}
}
