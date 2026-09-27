package com.abhishek.smarthome.home.domain.service;

import com.abhishek.smarthome.home.domain.entity.Home;
import com.abhishek.smarthome.home.domain.exception.HomeNotFoundException;
import com.abhishek.smarthome.home.domain.repository.HomeRepository;
import java.time.Clock;
import java.util.List;
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

	private final HomeRepository repository;
	private final Clock clock;

	@Transactional
	public Home register(String name, String timezone) {
		return repository.save(Home.register(name, timezone, clock.instant()));
	}

	/** All homes, ordered by name. */
	public List<Home> list() {
		return repository.findAll(Sort.by("name"));
	}

	/**
	 * @throws HomeNotFoundException if no home has this id
	 */
	public Home get(UUID id) {
		return repository.findById(id).orElseThrow(() -> new HomeNotFoundException(id));
	}
}
