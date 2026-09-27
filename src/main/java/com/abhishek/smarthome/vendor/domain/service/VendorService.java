package com.abhishek.smarthome.vendor.domain.service;

import com.abhishek.smarthome.vendor.VendorCode;
import com.abhishek.smarthome.vendor.domain.entity.Vendor;
import com.abhishek.smarthome.vendor.domain.exception.VendorNotFoundException;
import com.abhishek.smarthome.vendor.domain.repository.VendorRepository;
import java.time.Clock;
import java.util.UUID;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/** Registration and lookup of vendors. */
@Service
@RequiredArgsConstructor
@Transactional(readOnly = true)
public class VendorService {

	private final VendorRepository repository;
	private final Clock clock;

	@Transactional
	public Vendor register(VendorCode code, String name) {
		return repository.save(Vendor.register(code, name, clock.instant()));
	}

	/**
	 * @throws VendorNotFoundException if no vendor has this id
	 */
	public Vendor get(UUID id) {
		return repository.findById(id).orElseThrow(() -> new VendorNotFoundException(id));
	}
}
