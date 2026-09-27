package com.abhishek.smarthome.vendor.client;

import java.util.ArrayList;
import java.util.HashSet;
import java.util.Iterator;
import java.util.List;
import java.util.NoSuchElementException;
import java.util.Set;
import java.util.Spliterator;
import java.util.Spliterators;
import java.util.stream.Stream;
import java.util.stream.StreamSupport;
import org.jspecify.annotations.Nullable;

/**
 * Follows a vendor's pages until it reports no next cursor. Guards against runaway paging: at most
 * {@code maxPages} pages, and a cursor that repeats is an error instead of an endless loop.
 */
public final class VendorPaginator {

	/** Default page limit: 24 hours of per-minute samples at 60 per page is 24 pages; leave generous headroom. */
	public static final int DEFAULT_MAX_PAGES = 100;

	private VendorPaginator() {
	}

	/** All items of all pages, in order. */
	public static <T> List<T> fetchAll(PageFetcher<T> fetcher) {
		return fetchAll(fetcher, DEFAULT_MAX_PAGES);
	}

	/**
	 * @throws VendorPaginationException if more than {@code maxPages} pages are needed or a cursor repeats
	 */
	public static <T> List<T> fetchAll(PageFetcher<T> fetcher, int maxPages) {
		List<T> all = new ArrayList<>();
		pages(fetcher, maxPages).forEachRemaining(page -> all.addAll(page.getItems()));
		return all;
	}

	/**
	 * Items as a lazy stream: a page is fetched only when the previous one is consumed, so {@code limit(..)} or
	 * {@code findFirst()} stop calling the vendor early.
	 */
	public static <T> Stream<T> stream(PageFetcher<T> fetcher, int maxPages) {
		Iterator<VendorPage<T>> pages = pages(fetcher, maxPages);
		return StreamSupport.stream(Spliterators.spliteratorUnknownSize(pages, Spliterator.ORDERED), false)
				.flatMap(page -> page.getItems().stream());
	}

	private static <T> Iterator<VendorPage<T>> pages(PageFetcher<T> fetcher, int maxPages) {
		if (maxPages < 1) {
			throw new IllegalArgumentException("maxPages must be at least 1");
		}
		return new Iterator<>() {

			private final Set<String> seenCursors = new HashSet<>();
			private @Nullable String cursor;
			private int fetched;
			private boolean done;

			@Override
			public boolean hasNext() {
				return !done;
			}

			@Override
			public VendorPage<T> next() {
				if (done) {
					throw new NoSuchElementException();
				}
				if (fetched == maxPages) {
					throw new VendorPaginationException("Stopped after " + maxPages + " pages; next cursor " + cursor);
				}
				VendorPage<T> page = fetcher.fetch(cursor);
				fetched++;
				String next = page.getNextCursor();
				if (next == null) {
					done = true;
				}
				else if (!seenCursors.add(next)) {
					throw new VendorPaginationException("Vendor returned cursor '" + next + "' twice");
				}
				cursor = next;
				return page;
			}
		};
	}
}
