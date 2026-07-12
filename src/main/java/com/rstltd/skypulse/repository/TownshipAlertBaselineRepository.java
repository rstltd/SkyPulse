package com.rstltd.skypulse.repository;

import com.rstltd.skypulse.domain.alert.TownshipAlertBaseline;
import com.rstltd.skypulse.domain.alert.TownshipAlertBaselineId;
import org.springframework.data.jpa.repository.JpaRepository;

public interface TownshipAlertBaselineRepository
        extends JpaRepository<TownshipAlertBaseline, TownshipAlertBaselineId> {
}
