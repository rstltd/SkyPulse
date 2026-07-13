package com.rstltd.skypulse.service;

import com.rstltd.skypulse.api.dto.GnssQualityResponse;
import com.rstltd.skypulse.api.dto.context.*;
import com.rstltd.skypulse.domain.station.Station;
import com.rstltd.skypulse.repository.StationRepository;
import com.rstltd.skypulse.util.GeoUtils;
import com.rstltd.skypulse.util.TimeUtils;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;

import java.time.OffsetDateTime;
import java.util.ArrayList;
import java.util.List;
import java.util.Optional;
import java.util.Set;

/**
 * Direction B main product: integrate public environmental context for a coordinate. Stateless —
 * stores no sites. Finds the nearest station per domain (auto-radius), attaches provenance and
 * freshness, and never fails on "no data": missing blocks come back null with a matching warning.
 */
@Service
public class ContextService {

    private static final String CAP_RAINFALL = "RAINFALL";
    private static final String CAP_WATER_LEVEL = "WATER_LEVEL";

    private final StationRepository stationRepo;
    private final SpaceWeatherService spaceWeatherService;

    @Value("${skypulse.context.max-auto-radius-km}")
    private double maxAutoRadiusKm;

    @Value("${skypulse.context.quake-radius-km}")
    private double defaultQuakeRadiusKm;

    public ContextService(StationRepository stationRepo,
                          SpaceWeatherService spaceWeatherService) {
        this.stationRepo = stationRepo;
        this.spaceWeatherService = spaceWeatherService;
    }

    /**
     * @param radiusKm       null = auto (nearest within maxAutoRadiusKm); else a hard limit
     * @param rainRadiusKm   per-domain override for rainfall (defaults to radiusKm/auto)
     * @param waterRadiusKm  per-domain override for water level
     * @param quakeRadiusKm  seismic search radius around the point (defaults to config)
     * @param include        domains to compute (rainfall/seismic/gnss/water); null/empty = all
     */
    public ContextResponse getContext(double lat, double lon,
                                      Double radiusKm, Double rainRadiusKm, Double waterRadiusKm,
                                      Double quakeRadiusKm, Set<String> include) {
        OffsetDateTime now = TimeUtils.nowUtc();
        boolean autoRadius = radiusKm == null;
        double base = autoRadius ? maxAutoRadiusKm : radiusKm;
        double rainRadius = rainRadiusKm != null ? rainRadiusKm : base;
        double waterRadius = waterRadiusKm != null ? waterRadiusKm : base;
        double quakeRadius = quakeRadiusKm != null ? quakeRadiusKm : defaultQuakeRadiusKm;

        List<Warning> warnings = new ArrayList<>();
        boolean inTaiwan = GeoUtils.isInTaiwanRegion(lat, lon);
        if (!inTaiwan) {
            warnings.add(Warning.of("location", "OUTSIDE_COVERAGE",
                    "Coordinate is outside the Taiwan coverage area"));
        }

        // Location = county/township of the nearest rainfall station (fallback: water-level).
        Station locStation = nearest(CAP_RAINFALL, lat, lon, rainRadius)
                .or(() -> nearest(CAP_WATER_LEVEL, lat, lon, waterRadius))
                .orElse(null);
        LocationInfo location = new LocationInfo(
                locStation != null ? locStation.getCounty() : null,
                locStation != null ? locStation.getTownship() : null,
                inTaiwan);

        // GNSS quality is global (coordinate-independent) and always present.
        GnssQualityContext gnss = buildGnss();

        // Domain blocks (rainfall/seismic/water) are filled in later phases.
        RainfallContext rainfall = null;
        SeismicContext seismic = null;
        WaterLevelContext waterLevel = null;

        boolean wantRain = wants(include, "rainfall");
        boolean wantSeismic = wants(include, "seismic");
        boolean wantWater = wants(include, "water");
        boolean partial = (wantRain && rainfall == null)
                || (wantSeismic && seismic == null)
                || (wantWater && waterLevel == null);

        QueryEcho query = new QueryEcho(lat, lon, radiusKm, quakeRadius, autoRadius, maxAutoRadiusKm);
        ResponseMeta meta = new ResponseMeta(now, ResponseMeta.CONTRACT_VERSION, partial);

        return new ContextResponse(query, location, rainfall, seismic, gnss, waterLevel, warnings, meta);
    }

    private Optional<Station> nearest(String capability, double lat, double lon, double radiusKm) {
        return stationRepo.findNearestWithCapability(lat, lon, capability, radiusKm * 1000.0);
    }

    private GnssQualityContext buildGnss() {
        GnssQualityResponse q = spaceWeatherService.assessGnssQuality();
        List<GnssQualityContext.IndexProvenance> prov = List.of(
                new GnssQualityContext.IndexProvenance("Kp", "SWPC", null),
                new GnssQualityContext.IndexProvenance("Dst", "SWPC", null),
                new GnssQualityContext.IndexProvenance("solarWind", "SWPC", null));
        return new GnssQualityContext(
                q.qualityLevel() != null ? q.qualityLevel().name() : null,
                q.kpIndex(), q.dstIndex(), q.bzComponent(), q.solarWindSpeed(),
                q.gScale(), q.rScale(), q.sScale(),
                q.assessment(), q.recommendation(),
                true, prov, null);
    }

    private static boolean wants(Set<String> include, String domain) {
        return include == null || include.isEmpty() || include.contains(domain);
    }
}
