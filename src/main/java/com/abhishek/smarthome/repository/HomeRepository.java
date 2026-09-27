package com.abhishek.smarthome.repository;

import com.abhishek.smarthome.entity.Home;
import java.util.UUID;
import org.springframework.data.jpa.repository.JpaRepository;

public interface HomeRepository extends JpaRepository<Home, UUID> {
}
