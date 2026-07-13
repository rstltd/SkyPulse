package com.rstltd.skypulse.service;

import com.rstltd.skypulse.api.dto.GnssQualityResponse;
import com.rstltd.skypulse.api.dto.context.*;
import com.rstltd.skypulse.domain.alert.TownshipAlertBaseline;
import com.rstltd.skypulse.domain.alert.TownshipAlertBaselineId;
import com.rstltd.skypulse.domain.seismic.EarthquakeEvent;
import com.rstltd.skypulse.domain.station.Station;
import com.rstltd.skypulse.domain.station.StationCapability;
import com.rstltd.skypulse.domain.station.StationCapabilityId;
import com.rstltd.skypulse.domain.hydrology.WaterLevelObservation;
import com.rstltd.skypulse.domain.station.WaterLevelStation;
import com.rstltd.skypulse.domain.weather.RainfallObservation;
import com.rstltd.skypulse.repository.*;
import com.rstltd.skypulse.util.GeoUtils;
import com.rstltd.skypulse.util.TimeUtils;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.time.LocalDate;
import java.time.OffsetDateTime;
import java.time.ZoneId;
import java.util.ArrayList;
import java.util.Comparator;
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
    private static final int RAINFALL_LOOKBACK_HOURS = 72;
    private static final String SEISMIC_WINDOW = "30d";
    private static final ZoneId TAIPEI = ZoneId.of("Asia/Taipei");

    private final StationRepository stationRepo;
    private final StationCapabilityRepository capabilityRepo;
    private final RainfallObservationRepository rainfallRepo;
    private final WaterLevelObservationRepository waterLevelRepo;
    private final WaterLevelStationRepository waterLevelStationRepo;
    private final EarthquakeEventRepository earthquakeRepo;
    private final TownshipAlertBaselineRepository townshipRepo;
    private final SpaceWeatherService spaceWeatherService;
    private final SeismicService seismicService;

    @Value("${skypulse.context.max-auto-radius-km}")
    private double maxAutoRadiusKm;
    @Value("${skypulse.context.quake-radius-km}")
    private double defaultQuakeRadiusKm;
    @Value("${skypulse.context.freshness.rainfall-seconds}")
    private long rainfallSla;
    @Value("${skypulse.context.freshness.water-level-seconds}")
    private long waterLevelSla;
    @Value("${skypulse.context.freshness.seismic-seconds}")
    private long seismicSla;
    @Value("${skypulse.context.freshness.gnss-kp-seconds}")
    private long gnssKpSla;
    @Value("${skypulse.context.signal.yellow-fraction}")
    private double yellowFraction;

    public ContextService(StationRepository stationRepo,
                          StationCapabilityRepository capabilityRepo,
                          RainfallObservationRepository rainfallRepo,
                          WaterLevelObservationRepository waterLevelRepo,
                          WaterLevelStationRepository waterLevelStationRepo,
                          EarthquakeEventRepository earthquakeRepo,
                          TownshipAlertBaselineRepository townshipRepo,
                          SpaceWeatherService spaceWeatherService,
                          SeismicService seismicService) {
        this.stationRepo = stationRepo;
        this.capabilityRepo = capabilityRepo;
        this.rainfallRepo = rainfallRepo;
        this.waterLevelRepo = waterLevelRepo;
        this.waterLevelStationRepo = waterLevelStationRepo;
        this.earthquakeRepo = earthquakeRepo;
        this.townshipRepo = townshipRepo;
        this.spaceWeatherService = spaceWeatherService;
        this.seismicService = seismicService;
    }

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

        Station rainStation = nearest(CAP_RAINFALL, lat, lon, rainRadius).orElse(null);
        Station waterStation = nearest(CAP_WATER_LEVEL, lat, lon, waterRadius).orElse(null);

        Station locStation = rainStation != null ? rainStation : waterStation;
        LocationInfo location = new LocationInfo(
                locStation != null ? locStation.getCounty() : null,
                locStation != null ? locStation.getTownship() : null,
                inTaiwan);

        boolean wantRain = wants(include, "rainfall");
        boolean wantSeismic = wants(include, "seismic");
        boolean wantWater = wants(include, "water");

        RainfallContext rainfall = null;
        if (wantRain) {
            if (rainStation != null) {
                rainfall = buildRainfall(rainStation, lat, lon, now, warnings);
            } else {
                warnings.add(Warning.noStation("rainfall", rainRadius));
            }
        }

        WaterLevelContext waterLevel = null;
        if (wantWater) {
            if (waterStation != null) {
                waterLevel = buildWater(waterStation, lat, lon, now);
            } else {
                warnings.add(Warning.noStation("waterLevel", waterRadius));
            }
        }

        SeismicContext seismic = wantSeismic ? buildSeismic(lat, lon, quakeRadius, now) : null;

        GnssQualityContext gnss = buildGnss(now);

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

    // --- Rainfall -----------------------------------------------------------
    private RainfallContext buildRainfall(Station s, double lat, double lon,
                                          OffsetDateTime now, List<Warning> warnings) {
        String code = s.getStationCode();
        List<RainfallObservation> obs = rainfallRepo.findByStationCodeAndTimeBetween(
                code, now.minusHours(RAINFALL_LOOKBACK_HOURS), now);
        RainfallContext.AccumulatedMm acc = RainfallAggregator.accumulate(obs, now);
        BigDecimal maxI = RainfallAggregator.maxHourlyIntensity(obs);
        Freshness fresh = Freshness.of(RainfallAggregator.latestTime(obs), now, rainfallSla);
        Provenance prov = stationProvenance(s, CAP_RAINFALL, lat, lon);

        // SWCB effective accumulated rainfall (Rt) over the 7-day Asia/Taipei window + RTI = I x Rt.
        BigDecimal rt = effectiveRainfall(code, now);
        BigDecimal rti = maxI.multiply(rt).setScale(2, RoundingMode.HALF_UP);

        // Debris-flow alert baseline (R70) for the station's township -> warning signal.
        RainfallContext.AlertBaseline baseline = null;
        String signal = null;
        String signalBasis = null;
        String county = s.getCounty(), town = s.getTownship();
        Optional<TownshipAlertBaseline> tb = (county != null && town != null)
                ? townshipRepo.findById(new TownshipAlertBaselineId(county, town))
                : Optional.empty();
        if (tb.isPresent()) {
            BigDecimal threshold = tb.get().getAlertValue();
            baseline = new RainfallContext.AlertBaseline(town, threshold, "SWCB", "土石流警戒基準值", null);
            signal = SwcbEffectiveRainfall.signal(rt, threshold, yellowFraction);
            signalBasis = signalBasis(rt, threshold, signal);
        } else {
            warnings.add(Warning.of("rainfall", "NO_BASELINE_FOR_TOWNSHIP",
                    "No SWCB debris-flow baseline for township " + (town != null ? town : "(unknown)")));
        }

        return new RainfallContext(prov, fresh, acc, maxI, rt, rti, baseline, signal, signalBasis);
    }

    private BigDecimal effectiveRainfall(String code, OffsetDateTime now) {
        List<Object[]> rows = rainfallRepo.findDailyRain(code, now.minusDays(8));
        List<SwcbEffectiveRainfall.DailyRain> days = new ArrayList<>();
        for (Object[] row : rows) {
            LocalDate date = ((java.sql.Date) row[0]).toLocalDate();
            BigDecimal rain = row[1] == null ? null : new BigDecimal(row[1].toString());
            days.add(new SwcbEffectiveRainfall.DailyRain(date, rain));
        }
        LocalDate today = now.atZoneSameInstant(TAIPEI).toLocalDate();
        return SwcbEffectiveRainfall.effectiveRainfall(days, today);
    }

    private static String signalBasis(BigDecimal rt, BigDecimal r70, String signal) {
        if (signal == null) return null;
        int pct = rt.multiply(BigDecimal.valueOf(100))
                .divide(r70, 0, RoundingMode.HALF_UP).intValue();
        String label = switch (signal) {
            case SwcbEffectiveRainfall.RED -> "紅色警戒";
            case SwcbEffectiveRainfall.YELLOW -> "黃色警戒";
            default -> "綠燈（正常）";
        };
        String rel = SwcbEffectiveRainfall.RED.equals(signal) ? "已達警戒基準值" : "未逾越";
        return "有效累積雨量(" + rt + ") 為警戒基準值(" + r70 + ")的 " + pct + "%，" + rel + " → " + label;
    }

    // --- Water level --------------------------------------------------------
    private WaterLevelContext buildWater(Station s, double lat, double lon, OffsetDateTime now) {
        Optional<WaterLevelObservation> latest =
                waterLevelRepo.findFirstByStationCodeOrderByTimeDesc(s.getStationCode());
        BigDecimal level = latest.map(WaterLevelObservation::getWaterLevel).orElse(null);
        Freshness fresh = Freshness.of(latest.map(WaterLevelObservation::getTime).orElse(null), now, waterLevelSla);

        WaterLevelStation wls = waterLevelStationRepo.findById(s.getStationCode()).orElse(null);
        WaterLevelContext.AlertLevels levels = wls == null ? null : new WaterLevelContext.AlertLevels(
                wls.getAlertLevel1(), wls.getAlertLevel2(), wls.getAlertLevel3());
        String status = alertStatus(level, wls);

        return new WaterLevelContext(stationProvenance(s, CAP_WATER_LEVEL, lat, lon),
                fresh, level, levels, status);
    }

    /** Higher water level = worse. Returns null when level or thresholds are unknown. */
    static String alertStatus(BigDecimal level, WaterLevelStation wls) {
        if (level == null || wls == null) return null;
        if (wls.getAlertLevel3() != null && level.compareTo(wls.getAlertLevel3()) >= 0) return "LEVEL3";
        if (wls.getAlertLevel2() != null && level.compareTo(wls.getAlertLevel2()) >= 0) return "LEVEL2";
        if (wls.getAlertLevel1() != null && level.compareTo(wls.getAlertLevel1()) >= 0) return "LEVEL1";
        return "NORMAL";
    }

    // --- Seismic ------------------------------------------------------------
    private SeismicContext buildSeismic(double lat, double lon, double quakeRadiusKm, OffsetDateTime now) {
        List<EarthquakeEvent> nearby = seismicService.getNearbyEvents(lat, lon, quakeRadiusKm);
        EarthquakeEvent strongest = nearby.stream()
                .max(Comparator.comparing(EarthquakeEvent::getMagnitude))
                .orElse(null);
        SeismicContext.Event event = strongest == null ? null : toEvent(strongest, lat, lon);
        OffsetDateTime observedAt = earthquakeRepo.findFirstByOrderByTimeDesc()
                .map(EarthquakeEvent::getTime).orElse(null);
        String source = strongest != null ? strongest.getSource() : "CWA";
        Provenance prov = new Provenance(source, null, null, null, null, null, null);
        Freshness fresh = Freshness.of(observedAt, now, seismicSla);
        return new SeismicContext(event, nearby.size(), SEISMIC_WINDOW, prov, fresh);
    }

    private SeismicContext.Event toEvent(EarthquakeEvent e, double lat, double lon) {
        double dist = GeoUtils.distanceKm(lat, lon,
                e.getLatitude().doubleValue(), e.getLongitude().doubleValue());
        return new SeismicContext.Event(
                e.getEventId(), e.getTime(), e.getMagnitude(), e.getDepthKm(),
                e.getLatitude(), e.getLongitude(), round2(dist),
                e.getMaxIntensity(), e.getLocationDesc(), e.getSource());
    }

    // --- GNSS quality (global) ---------------------------------------------
    private GnssQualityContext buildGnss(OffsetDateTime now) {
        GnssQualityResponse q = spaceWeatherService.assessGnssQuality();
        OffsetDateTime kpAt = spaceWeatherService.getCurrentKp().map(k -> k.getTime()).orElse(null);
        OffsetDateTime dstAt = spaceWeatherService.getCurrentDst().map(d -> d.getTime()).orElse(null);
        OffsetDateTime swAt = spaceWeatherService.getCurrentSolarWind().map(w -> w.getTime()).orElse(null);
        List<GnssQualityContext.IndexProvenance> prov = List.of(
                new GnssQualityContext.IndexProvenance("Kp", "SWPC", kpAt),
                new GnssQualityContext.IndexProvenance("Dst", "SWPC", dstAt),
                new GnssQualityContext.IndexProvenance("solarWind", "SWPC", swAt));
        Freshness fresh = Freshness.of(kpAt, now, gnssKpSla);
        return new GnssQualityContext(
                q.qualityLevel() != null ? q.qualityLevel().name() : null,
                q.kpIndex(), q.dstIndex(), q.bzComponent(), q.solarWindSpeed(),
                q.gScale(), q.rScale(), q.sScale(),
                q.assessment(), q.recommendation(),
                true, prov, fresh);
    }

    // --- Provenance helper --------------------------------------------------
    private Provenance stationProvenance(Station s, String capability, double qLat, double qLon) {
        String dataset = capabilityRepo.findById(new StationCapabilityId(s.getStationCode(), capability))
                .map(StationCapability::getDatasetId).orElse(null);
        BigDecimal distKm = (s.getLatitude() != null && s.getLongitude() != null)
                ? round2(GeoUtils.distanceKm(qLat, qLon,
                        s.getLatitude().doubleValue(), s.getLongitude().doubleValue()))
                : null;
        return new Provenance(s.getSource(), dataset, s.getStationCode(), s.getStationName(),
                s.getLatitude(), s.getLongitude(), distKm);
    }

    private static BigDecimal round2(double v) {
        return BigDecimal.valueOf(v).setScale(2, RoundingMode.HALF_UP);
    }

    private static boolean wants(Set<String> include, String domain) {
        return include == null || include.isEmpty() || include.contains(domain);
    }
}
