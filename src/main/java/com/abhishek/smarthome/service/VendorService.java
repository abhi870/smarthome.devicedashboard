package com.abhishek.smarthome.service;

import com.abhishek.smarthome.entity.Vendor;
import com.abhishek.smarthome.enums.VendorCode;
import com.abhishek.smarthome.exception.VendorNotFoundException;
import com.abhishek.smarthome.repository.VendorRepository;
import java.time.Clock;
import java.util.List;
import java.util.UUID;
import lombok.RequiredArgsConstructor;
import org.springframework.data.domain.Sort;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/** Registration and lookup of vendors. */
@Service
@RequiredArgsConstructor
@Transactional(readOnly = true)
public class VendorService {

	private final VendorRepository vendorRepository;
	private final Clock clock;

	@Transactional
	public Vendor register(VendorCode code, String name) {
		return vendorRepository.save(Vendor.register(code, name, clock.instant()));
	}

	/** All vendors, ordered by code. */
	public List<Vendor> list() {
		return vendorRepository.findAll(Sort.by("code"));
	}

	/**
	 * @throws VendorNotFoundException if no vendor has this id
	 */
	public Vendor get(UUID id) {
		return vendorRepository.findById(id).orElseThrow(() -> new VendorNotFoundException(id));
	}
}
