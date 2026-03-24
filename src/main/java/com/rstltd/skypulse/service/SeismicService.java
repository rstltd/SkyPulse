package com.rstltd.skypulse.service;

import com.rstltd.skypulse.domain.seismic.EarthquakeEvent;
import com.rstltd.skypulse.repository.EarthquakeEventRepository;
import com.rstltd.skypulse.util.GeoUtils;
import com.rstltd.skypulse.util.TimeUtils;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.stereotype.Service;

import java.math.BigDecimal;
import java.time.OffsetDateTime;
import java.util.ArrayList;
import java.util.List;

@Service
public class SeismicService {

    private static final double DEDUP_TIME_WINDOW_SECONDS = 30;
    private static final double DEDUP_DISTANCE_KM = 10;
    private static final double DEDUP_MAGNITUDE_DIFF = 0.3;

    private final EarthquakeEventRepository earthquakeRepo;

    public SeismicService(EarthquakeEventRepository earthquakeRepo) {
        this.earthquakeRepo = earthquakeRepo;
    }

    public List<EarthquakeEvent> getLatestEvents() {
        OffsetDateTime now = TimeUtils.nowUtc();
        List<EarthquakeEvent> events = earthquakeRepo.findByTimeBetween(now.minusDays(7), now);
        return deduplicateEvents(events);
    }

    public Page<EarthquakeEvent> getEventsSince(OffsetDateTime since, BigDecimal minMag, Pageable pageable) {
        OffsetDateTime now = TimeUtils.nowUtc();
        if (minMag != null) {
            return earthquakeRepo.findByTimeBetweenAndMagnitudeGreaterThanEqual(since, now, minMag, pageable);
        }
        return earthquakeRepo.findByTimeBetween(since, now, pageable);
    }

    public List<EarthquakeEvent> getNearbyEvents(double lat, double lon, double radiusKm) {
        OffsetDateTime now = TimeUtils.nowUtc();
        List<EarthquakeEvent> recent = earthquakeRepo.findByTimeBetween(now.minusDays(30), now);
        List<EarthquakeEvent> nearby = recent.stream()
                .filter(e -> GeoUtils.distanceKm(
                        lat, lon,
                        e.getLatitude().doubleValue(),
                        e.getLongitude().doubleValue()) <= radiusKm)
                .toList();
        return deduplicateEvents(nearby);
    }

    /**
     * Check if a new earthquake event matches an existing one from a different source.
     * Used by collectors to prevent cross-source duplicates at ingestion time.
     */
    public boolean isDuplicate(OffsetDateTime time, double lat, double lon, double magnitude) {
        OffsetDateTime windowStart = time.minusSeconds((long) DEDUP_TIME_WINDOW_SECONDS);
        OffsetDateTime windowEnd = time.plusSeconds((long) DEDUP_TIME_WINDOW_SECONDS);
        List<EarthquakeEvent> candidates = earthquakeRepo.findByTimeBetween(windowStart, windowEnd);

        return candidates.stream().anyMatch(e ->
                GeoUtils.distanceKm(lat, lon,
                        e.getLatitude().doubleValue(),
                        e.getLongitude().doubleValue()) <= DEDUP_DISTANCE_KM
                && Math.abs(magnitude - e.getMagnitude().doubleValue()) <= DEDUP_MAGNITUDE_DIFF);
    }

    /**
     * Remove duplicate events from list. When CWA and USGS report the same earthquake,
     * prefer CWA (local Taiwan authority, more precise for local events).
     */
    List<EarthquakeEvent> deduplicateEvents(List<EarthquakeEvent> events) {
        List<EarthquakeEvent> result = new ArrayList<>();
        for (var event : events) {
            boolean isDup = result.stream().anyMatch(existing ->
                    isSamePhysicalEvent(existing, event));
            if (!isDup) {
                result.add(event);
            } else if ("CWA".equals(event.getSource())) {
                // Replace USGS with CWA version
                result.removeIf(existing ->
                        isSamePhysicalEvent(existing, event) && "USGS".equals(existing.getSource()));
                result.add(event);
            }
        }
        return result;
    }

    private boolean isSamePhysicalEvent(EarthquakeEvent a, EarthquakeEvent b) {
        long timeDiff = Math.abs(
                a.getTime().toEpochSecond() - b.getTime().toEpochSecond());
        if (timeDiff > DEDUP_TIME_WINDOW_SECONDS) return false;

        double distance = GeoUtils.distanceKm(
                a.getLatitude().doubleValue(), a.getLongitude().doubleValue(),
                b.getLatitude().doubleValue(), b.getLongitude().doubleValue());
        if (distance > DEDUP_DISTANCE_KM) return false;

        double magDiff = Math.abs(
                a.getMagnitude().doubleValue() - b.getMagnitude().doubleValue());
        return magDiff <= DEDUP_MAGNITUDE_DIFF;
    }
}
