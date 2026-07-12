package com.rstltd.skypulse.repository;

import com.rstltd.skypulse.domain.spaceweather.NoaaScale;
import com.rstltd.skypulse.domain.spaceweather.NoaaScaleId;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.Optional;

public interface NoaaScaleRepository extends JpaRepository<NoaaScale, NoaaScaleId> {
    /** Latest continuously-observed scales (horizon = "observed"). */
    Optional<NoaaScale> findTopByHorizonOrderByTimeDesc(String horizon);
}
