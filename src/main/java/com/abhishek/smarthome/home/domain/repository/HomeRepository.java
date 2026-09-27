package com.abhishek.smarthome.home.domain.repository;

import com.abhishek.smarthome.home.domain.entity.Home;
import java.util.UUID;
import org.springframework.data.jpa.repository.JpaRepository;

public interface HomeRepository extends JpaRepository<Home, UUID> {
}
