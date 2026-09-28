package com.abhishek.smarthome.service;

import com.abhishek.smarthome.dto.output.home.HomeResponse;
import com.abhishek.smarthome.entity.Home;
import com.abhishek.smarthome.exception.HomeNotFoundException;
import com.abhishek.smarthome.repository.HomeRepository;
import java.time.Instant;
import java.util.Collection;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import lombok.RequiredArgsConstructor;
import org.springframework.data.domain.Sort;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/** Registration and lookup of homes. */
@Service
@RequiredArgsConstructor
@Transactional(readOnly = true)
public class HomeService {

	private final HomeRepository homeRepository;

	@Transactional
	public HomeResponse register(String name, String timezone) {
		return HomeResponse.from(homeRepository.save(Home.register(name, timezone, Instant.now())));
	}

	/** All homes, ordered by name. */
	public List<HomeResponse> list() {
		return homeRepository.findAll(Sort.by("name")).stream().map(HomeResponse::from).toList();
	}

	/**
	 * @throws HomeNotFoundException if no home has this id
	 */
	public HomeResponse get(UUID id) {
		return HomeResponse.from(getHome(id));
	}

	/**
	 * The entity itself, for other services (e.g. to register a home device in it).
	 *
	 * @throws HomeNotFoundException if no home has this id
	 */
	public Home getHome(UUID id) {
		return homeRepository.findById(id).orElseThrow(() -> new HomeNotFoundException(id));
	}

	/** Homes with these ids, keyed by id (one query), for other services assembling related data. */
	public Map<UUID, Home> getHomesById(Collection<UUID> ids) {
		return EntityLookups.indexById(homeRepository.findAllById(ids), Home::getId);
	}
}
