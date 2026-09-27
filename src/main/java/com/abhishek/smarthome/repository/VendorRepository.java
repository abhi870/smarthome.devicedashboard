package com.abhishek.smarthome.repository;

import com.abhishek.smarthome.entity.Vendor;
import java.util.UUID;
import org.springframework.data.jpa.repository.JpaRepository;

public interface VendorRepository extends JpaRepository<Vendor, UUID> {
}
