package com.abhishek.smarthome.vendor.client;

import java.util.List;
import lombok.Getter;
import org.jspecify.annotations.Nullable;

/**
 * One page of a vendor response: its items and the cursor for the next page, or {@code null} when this is the last
 * page. The cursor is whatever the vendor pages by — a page number (Samsung), an opaque token (Amazon) or a
 * timestamp (Cisco) — kept as text so {@link VendorPaginator} can follow any of them.
 */
@Getter
public final class VendorPage<T> {

	private final List<T> items;
	private final @Nullable String nextCursor;

	private VendorPage(List<T> items, @Nullable String nextCursor) {
		this.items = List.copyOf(items);
		this.nextCursor = nextCursor;
	}

	/** A page followed by another one at {@code nextCursor}. */
	public static <T> VendorPage<T> of(List<T> items, String nextCursor) {
		return new VendorPage<>(items, nextCursor);
	}

	/** The last page. */
	public static <T> VendorPage<T> last(List<T> items) {
		return new VendorPage<>(items, null);
	}

	public boolean hasNext() {
		return nextCursor != null;
	}
}
