package com.abhishek.smarthome.service;

import java.util.Collection;
import java.util.Map;
import java.util.Set;
import java.util.UUID;
import java.util.function.Function;
import java.util.stream.Collectors;

/** Helpers for loading related rows in bulk: collect the foreign keys, fetch once, index by id. */
public final class EntityLookups {

	private EntityLookups() {
	}

	/** The distinct ids {@code idGetter} returns for {@code items} (e.g. the vendor ids of some devices). */
	public static <T> Set<UUID> idsOf(Collection<T> items, Function<T, UUID> idGetter) {
		return items.stream().map(idGetter).collect(Collectors.toSet());
	}

	/** {@code entities} keyed by their id. */
	public static <T> Map<UUID, T> indexById(Collection<T> entities, Function<T, UUID> idGetter) {
		return entities.stream().collect(Collectors.toMap(idGetter, Function.identity()));
	}
}
