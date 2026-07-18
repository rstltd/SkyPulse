package com.rstltd.skypulse.repository;

import com.rstltd.skypulse.domain.station.StationCapability;
import com.rstltd.skypulse.domain.station.StationCapabilityId;
import org.springframework.data.jpa.repository.JpaRepository;

public interface StationCapabilityRepository
        extends JpaRepository<StationCapability, StationCapabilityId> {
}
