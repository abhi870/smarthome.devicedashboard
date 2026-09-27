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

	/** The nested object at {@code path}; empty if any step is missing. */
	static Map<String, Object> at(@Nullable Object json, String... path) {
		Map<String, Object> current = map(json);
		for (String key : path) {
			current = map(current.get(key));
		}
		return current;
	}

	/** The list under {@code key} as objects; empty if missing. */
	static List<Map<String, Object>> objects(Map<String, Object> json, String key) {
		Object value = json.get(key);
		return value instanceof List<?> list ? list.stream().map(JsonMaps::map).toList() : List.of();
	}

	static long longValue(@Nullable Object value, String field) {
		if (value instanceof Number number) {
			return number.longValue();
		}
		throw new IllegalStateException("Vendor response field '" + field + "' is not a number: " + value);
	}
}
