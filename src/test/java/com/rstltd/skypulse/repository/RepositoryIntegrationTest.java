package com.rstltd.skypulse.repository;

import com.rstltd.skypulse.IntegrationTestBase;
import com.rstltd.skypulse.domain.alert.HazardAlert;
import com.rstltd.skypulse.domain.hydrology.ReservoirStatus;
import com.rstltd.skypulse.domain.hydrology.WaterLevelObservation;
import com.rstltd.skypulse.domain.seismic.EarthquakeEvent;
import com.rstltd.skypulse.domain.spaceweather.DstIndexRecord;
import com.rstltd.skypulse.domain.spaceweather.KpIndexRecord;
import com.rstltd.skypulse.domain.spaceweather.SolarWindRecord;
import com.rstltd.skypulse.domain.spaceweather.SpaceWeatherAlert;
import com.rstltd.skypulse.domain.station.Station;
import com.rstltd.skypulse.domain.weather.RainfallObservation;
import com.rstltd.skypulse.domain.weather.WeatherForecast;
import com.rstltd.skypulse.domain.weather.WeatherObservation;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;
import java.time.OffsetDateTime;
import java.time.ZoneOffset;
import java.util.List;

import static org.junit.jupiter.api.Assertions.*;

@Transactional
class RepositoryIntegrationTest extends IntegrationTestBase {

    @Autowired StationRepository stationRepo;
    @Autowired RainfallObservationRepository rainfallRepo;
    @Autowired WeatherObservationRepository weatherObsRepo;
    @Autowired WeatherForecastRepository forecastRepo;
    @Autowired EarthquakeEventRepository earthquakeRepo;
    @Autowired KpIndexRecordRepository kpRepo;
    @Autowired DstIndexRecordRepository dstRepo;
    @Autowired SolarWindRecordRepository solarWindRepo;
    @Autowired SpaceWeatherAlertRepository swAlertRepo;
    @Autowired WaterLevelObservationRepository waterLevelRepo;
    @Autowired ReservoirStatusRepository reservoirRepo;
    @Autowired HazardAlertRepository hazardAlertRepo;

    private static final OffsetDateTime T1 = OffsetDateTime.of(2024, 1, 15, 8, 0, 0, 0, ZoneOffset.UTC);
    private static final OffsetDateTime T2 = OffsetDateTime.of(2024, 1, 15, 9, 0, 0, 0, ZoneOffset.UTC);
    private static final OffsetDateTime T3 = OffsetDateTime.of(2024, 1, 15, 10, 0, 0, 0, ZoneOffset.UTC);

    @Test
    void saveAndFindStation() {
        Station s = new Station();
        s.setStationCode("C0D660");
        s.setStationName("日月潭");
        s.setSource("CWA");
        s.setStationType("RAINFALL");
        s.setLatitude(new BigDecimal("23.881200"));
        s.setLongitude(new BigDecimal("120.908100"));
        s.setIsActive(true);

        Station saved = stationRepo.saveAndFlush(s);
        assertNotNull(saved.getId());

        assertTrue(stationRepo.existsByStationCode("C0D660"));
        var found = stationRepo.findByStationCode("C0D660");
        assertTrue(found.isPresent());
        assertEquals("日月潭", found.get().getStationName());
    }

    @Test
    void saveAndFindRainfallObservation() {
        RainfallObservation r = new RainfallObservation();
        r.setTime(T1);
        r.setStationCode("C0D660");
        r.setPrecipitation(new BigDecimal("12.50"));
        r.setSource("CWA");
        r.setRawData("{\"hourly\":12.5}");

        rainfallRepo.saveAndFlush(r);

        List<RainfallObservation> results = rainfallRepo.findByStationCodeAndTimeBetween(
                "C0D660", T1.minusHours(1), T1.plusHours(1));
        assertEquals(1, results.size());
        assertEquals(new BigDecimal("12.50"), results.get(0).getPrecipitation());
    }

    @Test
    void rainfallFindByTimeBetween() {
        for (var t : List.of(T1, T2, T3)) {
            RainfallObservation r = new RainfallObservation();
            r.setTime(t);
            r.setStationCode("TEST01");
            r.setPrecipitation(new BigDecimal("5.00"));
            r.setSource("CWA");
            rainfallRepo.save(r);
        }
        rainfallRepo.flush();

        List<RainfallObservation> results = rainfallRepo.findByTimeBetween(T1, T2.plusMinutes(1));
        assertEquals(2, results.size());
    }

    @Test
    void saveAndFindWeatherObservation() {
        WeatherObservation w = new WeatherObservation();
        w.setTime(T1);
        w.setStationCode("466920");
        w.setTemperature(new BigDecimal("22.50"));
        w.setHumidity(new BigDecimal("78.00"));
        w.setSource("CWA");

        weatherObsRepo.saveAndFlush(w);

        var results = weatherObsRepo.findByStationCodeAndTimeBetween("466920", T1.minusHours(1), T1.plusHours(1));
        assertEquals(1, results.size());
    }

    @Test
    void saveAndFindWeatherForecast() {
        WeatherForecast f = new WeatherForecast();
        f.setLocationName("南投縣");
        f.setForecastTime(T2);
        f.setIssuedTime(T1);
        f.setWeatherDesc("多雲時陰短暫雨");
        f.setRainProb(70);
        f.setSource("CWA");

        forecastRepo.saveAndFlush(f);
        assertNotNull(f.getId());

        var results = forecastRepo.findByLocationNameAndForecastTimeAfter("南投縣", T1);
        assertEquals(1, results.size());
    }

    @Test
    void saveAndFindEarthquakeEvent() {
        EarthquakeEvent e = new EarthquakeEvent();
        e.setTime(T1);
        e.setEventId("us7000abc1");
        e.setMagnitude(new BigDecimal("5.20"));
        e.setDepthKm(new BigDecimal("15.00"));
        e.setLatitude(new BigDecimal("23.500000"));
        e.setLongitude(new BigDecimal("121.000000"));
        e.setSource("USGS");
        e.setRawData("{\"type\":\"earthquake\"}");

        earthquakeRepo.saveAndFlush(e);

        assertTrue(earthquakeRepo.existsByEventId("us7000abc1"));
        var found = earthquakeRepo.findByEventId("us7000abc1");
        assertTrue(found.isPresent());
        assertEquals(new BigDecimal("5.20"), found.get().getMagnitude());
    }

    @Test
    void earthquakeFindByMagnitude() {
        for (var mag : List.of("4.00", "5.50", "6.20")) {
            EarthquakeEvent e = new EarthquakeEvent();
            e.setTime(T1.plusMinutes(Integer.parseInt(mag.substring(0, 1))));
            e.setEventId("evt-" + mag);
            e.setMagnitude(new BigDecimal(mag));
            e.setLatitude(new BigDecimal("23.5"));
            e.setLongitude(new BigDecimal("121.0"));
            e.setSource("USGS");
            earthquakeRepo.save(e);
        }
        earthquakeRepo.flush();

        var results = earthquakeRepo.findByTimeBetweenAndMagnitudeGreaterThanEqual(
                T1.minusHours(1), T1.plusHours(1), new BigDecimal("5.0"));
        assertEquals(2, results.size());
    }

    @Test
    void saveAndFindKpIndex() {
        KpIndexRecord kp1 = new KpIndexRecord();
        kp1.setTime(T1);
        kp1.setKpValue(new BigDecimal("3.0"));
        kp1.setSource("SWPC");

        KpIndexRecord kp2 = new KpIndexRecord();
        kp2.setTime(T2);
        kp2.setKpValue(new BigDecimal("5.7"));
        kp2.setSource("SWPC");

        kpRepo.saveAllAndFlush(List.of(kp1, kp2));

        var latest = kpRepo.findTopByOrderByTimeDesc();
        assertTrue(latest.isPresent());
        assertEquals(new BigDecimal("5.7"), latest.get().getKpValue());
    }

    @Test
    void saveAndFindDstIndex() {
        DstIndexRecord dst = new DstIndexRecord();
        dst.setTime(T1);
        dst.setDstValue(new BigDecimal("-45.0"));
        dst.setSource("WDC_KYOTO");

        dstRepo.saveAndFlush(dst);

        var latest = dstRepo.findTopByOrderByTimeDesc();
        assertTrue(latest.isPresent());
        assertEquals(new BigDecimal("-45.0"), latest.get().getDstValue());
    }

    @Test
    void saveAndFindSolarWind() {
        SolarWindRecord sw = new SolarWindRecord();
        sw.setTime(T1);
        sw.setWindSpeed(new BigDecimal("450.00"));
        sw.setDensity(new BigDecimal("5.20"));
        sw.setBz(new BigDecimal("-3.50"));
        sw.setSource("SWPC");

        solarWindRepo.saveAndFlush(sw);

        var latest = solarWindRepo.findTopByOrderByTimeDesc();
        assertTrue(latest.isPresent());
        assertEquals(new BigDecimal("450.00"), latest.get().getWindSpeed());
    }

    @Test
    void saveAndFindSpaceWeatherAlert() {
        SpaceWeatherAlert alert = new SpaceWeatherAlert();
        alert.setAlertTime(T1);
        alert.setAlertType("WARNING");
        alert.setSerialNumber("WATA20240115");
        alert.setGScale(2);
        alert.setSource("SWPC");

        swAlertRepo.saveAndFlush(alert);
        assertNotNull(alert.getId());

        var found = swAlertRepo.findBySerialNumber("WATA20240115");
        assertTrue(found.isPresent());
    }

    @Test
    void saveAndFindWaterLevel() {
        WaterLevelObservation wl = new WaterLevelObservation();
        wl.setTime(T1);
        wl.setStationCode("1140H053");
        wl.setWaterLevel(new BigDecimal("12.345"));
        wl.setSource("WRA");

        waterLevelRepo.saveAndFlush(wl);

        var results = waterLevelRepo.findByStationCodeAndTimeBetweenOrderByTimeAsc("1140H053", T1.minusHours(1), T1.plusHours(1));
        assertEquals(1, results.size());
    }

    @Test
    void saveAndFindReservoir() {
        ReservoirStatus rs = new ReservoirStatus();
        rs.setTime(T1);
        rs.setReservoirId("10201");
        rs.setReservoirName("翡翠水庫");
        rs.setWaterLevel(new BigDecimal("165.200"));
        rs.setStoragePct(new BigDecimal("85.30"));
        rs.setSource("WRA");

        reservoirRepo.saveAndFlush(rs);

        var results = reservoirRepo.findByReservoirIdAndTimeBetween("10201", T1.minusHours(1), T1.plusHours(1));
        assertEquals(1, results.size());
        assertEquals("翡翠水庫", results.get(0).getReservoirName());
    }

    @Test
    void saveAndFindHazardAlert() {
        HazardAlert ha = new HazardAlert();
        ha.setAlertTime(T1);
        ha.setAlertType("HEAVY_RAIN");
        ha.setSeverity("ORANGE");
        ha.setSource("CWA");
        ha.setSourceAlertId("CWA-2024-001");
        ha.setTitle("豪雨特報");
        ha.setRawData("{\"status\":\"active\"}");

        hazardAlertRepo.saveAndFlush(ha);
        assertNotNull(ha.getId());

        var found = hazardAlertRepo.findBySourceAlertId("CWA-2024-001");
        assertTrue(found.isPresent());
        assertEquals("豪雨特報", found.get().getTitle());
    }

    @Test
    void jsonbRoundTrip() {
        String json = "{\"key\":\"value\",\"nested\":{\"num\":42}}";

        RainfallObservation r = new RainfallObservation();
        r.setTime(T1);
        r.setStationCode("JSON_TEST");
        r.setPrecipitation(BigDecimal.ZERO);
        r.setSource("TEST");
        r.setRawData(json);

        rainfallRepo.saveAndFlush(r);

        var results = rainfallRepo.findByStationCodeAndTimeBetween("JSON_TEST", T1.minusHours(1), T1.plusHours(1));
        assertEquals(1, results.size());
        assertEquals(json, results.get(0).getRawData());
    }
}
