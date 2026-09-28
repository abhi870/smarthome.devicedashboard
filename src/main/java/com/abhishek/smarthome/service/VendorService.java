package com.abhishek.smarthome.service;

import com.abhishek.smarthome.dto.output.vendor.VendorResponse;
import com.abhishek.smarthome.entity.Vendor;
import com.abhishek.smarthome.enums.VendorCode;
import com.abhishek.smarthome.exception.VendorNotFoundException;
import com.abhishek.smarthome.repository.VendorRepository;
import java.time.Instant;
import java.util.Collection;
import java.util.List;
import java.util.Map;
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

	@Transactional
	public VendorResponse register(VendorCode code, String name) {
		return VendorResponse.from(vendorRepository.save(Vendor.register(code, name, Instant.now())));
	}

	/** All vendors, ordered by code. */
	public List<VendorResponse> list() {
		return vendorRepository.findAll(Sort.by("code")).stream().map(VendorResponse::from).toList();
	}

	/**
	 * @throws VendorNotFoundException if no vendor has this id
	 */
	public VendorResponse get(UUID id) {
		return VendorResponse.from(getVendor(id));
	}

	/**
	 * The entity itself, for other services (e.g. to attach a new catalogue device to it).
	 *
	 * @throws VendorNotFoundException if no vendor has this id
	 */
	public Vendor getVendor(UUID id) {
		return vendorRepository.findById(id).orElseThrow(() -> new VendorNotFoundException(id));
	}

	/** Vendors with these ids, keyed by id (one query), for other services assembling related data. */
	public Map<UUID, Vendor> getVendorsById(Collection<UUID> ids) {
		return EntityLookups.indexById(vendorRepository.findAllById(ids), Vendor::getId);
	}
}
