package com.rstltd.skypulse.repository;

import com.rstltd.skypulse.domain.hydrology.Reservoir;
import org.springframework.data.jpa.repository.JpaRepository;

public interface ReservoirRepository extends JpaRepository<Reservoir, String> {
}
