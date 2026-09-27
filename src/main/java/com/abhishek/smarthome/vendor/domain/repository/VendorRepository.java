package com.abhishek.smarthome.vendor.domain.repository;

import com.abhishek.smarthome.vendor.domain.entity.Vendor;
import java.util.UUID;
import org.springframework.data.jpa.repository.JpaRepository;

public interface VendorRepository extends JpaRepository<Vendor, UUID> {
}
