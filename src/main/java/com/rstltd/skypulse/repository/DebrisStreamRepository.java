package com.rstltd.skypulse.repository;

import com.rstltd.skypulse.domain.alert.DebrisStream;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;

public interface DebrisStreamRepository extends JpaRepository<DebrisStream, String> {
    List<DebrisStream> findByCountyAndTown(String county, String town);
}
