package com.rstltd.skypulse.repository;

import com.rstltd.skypulse.domain.station.WaterLevelStation;
import org.springframework.data.jpa.repository.JpaRepository;

public interface WaterLevelStationRepository extends JpaRepository<WaterLevelStation, String> {
}
