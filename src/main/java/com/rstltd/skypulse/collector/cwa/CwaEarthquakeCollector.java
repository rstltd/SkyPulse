package com.rstltd.skypulse.collector.cwa;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.rstltd.skypulse.collector.common.CollectorBase;
import com.rstltd.skypulse.collector.common.CollectorResult;
import com.rstltd.skypulse.collector.cwa.dto.CwaEarthquakeResponse;
import com.rstltd.skypulse.domain.seismic.EarthquakeEvent;
import com.rstltd.skypulse.domain.seismic.EarthquakeStationIntensity;
import com.rstltd.skypulse.repository.EarthquakeEventRepository;
import com.rstltd.skypulse.repository.EarthquakeStationIntensityRepository;
import com.rstltd.skypulse.util.SeismicIntensity;
import com.rstltd.skypulse.util.TimeUtils;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;
import reactor.core.publisher.Mono;

import java.math.BigDecimal;
import java.time.OffsetDateTime;
import java.util.ArrayList;
import java.util.Collections;
import java.util.HashSet;
import java.util.List;
import java.util.Set;

@Component
public class CwaEarthquakeCollector extends CollectorBase<CwaEarthquakeResponse.Earthquake> {

    private final CwaApiClient cwaApiClient;
    private final EarthquakeEventRepository earthquakeRepo;
    private final EarthquakeStationIntensityRepository stationIntensityRepo;
    private final ObjectMapper objectMapper;

    @Value("${skypulse.cwa.earthquake.min-magnitude}")
    private double minMagnitude;

    public CwaEarthquakeCollector(CwaApiClient cwaApiClient,
                                  EarthquakeEventRepository earthquakeRepo,
                                  EarthquakeStationIntensityRepository stationIntensityRepo,
                                  ObjectMapper objectMapper) {
        this.cwaApiClient = cwaApiClient;
        this.earthquakeRepo = earthquakeRepo;
        this.stationIntensityRepo = stationIntensityRepo;
        this.objectMapper = objectMapper;
    }

    @Scheduled(cron = "${skypulse.cwa.schedule.earthquake}")
    public CollectorResult collect() {
        return execute();
    }

    @Override
    protected String getSourceName() {
        return "CWA_EARTHQUAKE";
    }

    @Override
    protected Mono<List<CwaEarthquakeResponse.Earthquake>> fetch() {
        return cwaApiClient.getDataset("E-A0015-001", CwaEarthquakeResponse.class)
                .map(response -> {
                    if (response.records() != null && response.records().Earthquake() != null) {
                        return response.records().Earthquake();
                    }
                    return Collections.<CwaEarthquakeResponse.Earthquake>emptyList();
                });
    }

    @Override
    protected boolean validate(CwaEarthquakeResponse.Earthquake item) {
        if (item.EarthquakeInfo() == null || item.EarthquakeInfo().EarthquakeMagnitude() == null) {
            return false;
        }
        return item.EarthquakeInfo().EarthquakeMagnitude().MagnitudeValue() >= minMagnitude
                && item.EarthquakeInfo().OriginTime() != null
                && item.EarthquakeNo() > 0;
    }

    @Override
    protected int persist(List<CwaEarthquakeResponse.Earthquake> data) {
        int count = 0;
        for (var eq : data) {
            String eventId = "CWA-" + eq.EarthquakeNo();
            if (earthquakeRepo.existsByEventId(eventId)) {
                continue;
            }
            EarthquakeEvent entity = mapToEntity(eq, eventId);
            List<EarthquakeStationIntensity> stations = mapStations(eq, eventId, entity.getTime());
            applyMaxIntensity(entity, stations);
            earthquakeRepo.save(entity);
            if (!stations.isEmpty()) {
                stationIntensityRepo.saveAll(stations);
            }
            count++;
        }
        if (count > 0) {
            earthquakeRepo.flush();
            stationIntensityRepo.flush();
        }
        return count;
    }

    private EarthquakeEvent mapToEntity(CwaEarthquakeResponse.Earthquake eq, String eventId) {
        var info = eq.EarthquakeInfo();
        EarthquakeEvent entity = new EarthquakeEvent();
        entity.setTime(TimeUtils.toUtcOffset(
                TimeUtils.parseCwaTimestamp(info.OriginTime())));
        entity.setEventId(eventId);
        entity.setMagnitude(BigDecimal.valueOf(info.EarthquakeMagnitude().MagnitudeValue()));
        entity.setDepthKm(BigDecimal.valueOf(info.FocalDepth()));
        entity.setLatitude(BigDecimal.valueOf(info.Epicenter().EpicenterLatitude()));
        entity.setLongitude(BigDecimal.valueOf(info.Epicenter().EpicenterLongitude()));
        entity.setLocationDesc(info.Epicenter().Location());
        entity.setSource("CWA");
        try {
            entity.setRawData(objectMapper.writeValueAsString(eq));
        } catch (JsonProcessingException e) {
            log.warn("[CWA_EARTHQUAKE] Failed to serialize raw data for event {}", eventId);
        }
        return entity;
    }

    /** Flatten Intensity.ShakingArea[].EqStation[] into per-station intensity rows. */
    private List<EarthquakeStationIntensity> mapStations(CwaEarthquakeResponse.Earthquake eq,
                                                         String eventId, OffsetDateTime time) {
        List<EarthquakeStationIntensity> result = new ArrayList<>();
        if (eq.Intensity() == null || eq.Intensity().ShakingArea() == null) {
            return result;
        }
        Set<String> seen = new HashSet<>();
        for (var area : eq.Intensity().ShakingArea()) {
            if (area.EqStation() == null) continue;
            for (var st : area.EqStation()) {
                if (st.StationID() == null || st.StationID().isBlank()) continue;
                // A station belongs to exactly one intensity area; guard against dup PKs anyway.
                if (!seen.add(st.StationID())) continue;
                EarthquakeStationIntensity s = new EarthquakeStationIntensity();
                s.setEventId(eventId);
                s.setStationCode(st.StationID());
                s.setTime(time);
                s.setStationName(st.StationName());
                s.setCounty(area.CountyName());
                s.setIntensity(st.SeismicIntensity());
                s.setIntensityRank(SeismicIntensity.rankOf(st.SeismicIntensity()));
                s.setPgaGal(scaleValue(st.pga()));
                s.setPgvCms(scaleValue(st.pgv()));
                if (st.StationLatitude() != null) {
                    s.setStationLat(BigDecimal.valueOf(st.StationLatitude()));
                }
                if (st.StationLongitude() != null) {
                    s.setStationLon(BigDecimal.valueOf(st.StationLongitude()));
                }
                result.add(s);
            }
        }
        return result;
    }

    /** The event's max intensity is the strongest reporting station. */
    private void applyMaxIntensity(EarthquakeEvent entity, List<EarthquakeStationIntensity> stations) {
        EarthquakeStationIntensity max = null;
        for (var s : stations) {
            if (s.getIntensityRank() == null) continue;
            if (max == null || s.getIntensityRank() > max.getIntensityRank()) {
                max = s;
            }
        }
        if (max != null) {
            entity.setMaxIntensity(max.getIntensity());
            entity.setMaxIntensityRank(max.getIntensityRank());
        }
    }

    private BigDecimal scaleValue(CwaEarthquakeResponse.PgaPgv p) {
        if (p == null || p.IntScaleValue() == null) return null;
        return BigDecimal.valueOf(p.IntScaleValue());
    }
}
