package com.abhishek.smarthome.vendor.client;

import org.jspecify.annotations.Nullable;

/**
 * Fetches one page from a vendor. {@code cursor} is {@code null} for the first page, otherwise the
 * {@link VendorPage#getNextCursor()} of the previous page. Each vendor client implements this with its own paging
 * parameters, e.g. Samsung {@code page/size}, Amazon {@code maxResults/nextToken}, Cisco {@code since/limit}.
 */
@FunctionalInterface
public interface PageFetcher<T> {

	VendorPage<T> fetch(@Nullable String cursor);
}
