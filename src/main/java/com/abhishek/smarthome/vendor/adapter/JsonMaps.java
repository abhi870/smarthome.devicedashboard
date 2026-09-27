package com.abhishek.smarthome.vendor.adapter;

import java.util.List;
import java.util.Map;
import org.jspecify.annotations.Nullable;
import org.springframework.core.ParameterizedTypeReference;

/** Helpers for reading vendor JSON bodies deserialized as plain maps and lists. */
final class JsonMaps {

	static final ParameterizedTypeReference<Map<String, Object>> MAP = new ParameterizedTypeReference<>() {
	};

	private JsonMaps() {
	}

	@SuppressWarnings("unchecked")
	static Map<String, Object> map(@Nullable Object value) {
		return value instanceof Map<?, ?> map ? (Map<String, Object>) map : Map.of();
	}

	@SuppressWarnings("unchecked")
	static List<Object> list(@Nullable Object value) {
		return value instanceof List<?> list ? (List<Object>) list : List.of();
	}

	static long longValue(@Nullable Object value, String field) {
		if (value instanceof Number number) {
			return number.longValue();
		}
		throw new IllegalStateException("Vendor response field '" + field + "' is not a number: " + value);
	}
}
