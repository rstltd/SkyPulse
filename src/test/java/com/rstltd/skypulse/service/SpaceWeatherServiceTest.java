package com.rstltd.skypulse.service;

import com.rstltd.skypulse.api.dto.GnssQualityLevel;
import com.rstltd.skypulse.api.dto.GnssQualityResponse;
import com.rstltd.skypulse.domain.spaceweather.DstIndexRecord;
import com.rstltd.skypulse.domain.spaceweather.KpIndexRecord;
import com.rstltd.skypulse.domain.spaceweather.SolarWindRecord;
import com.rstltd.skypulse.repository.DstIndexRecordRepository;
import com.rstltd.skypulse.repository.KpIndexRecordRepository;
import com.rstltd.skypulse.repository.SolarWindRecordRepository;
import com.rstltd.skypulse.repository.SpaceWeatherAlertRepository;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.math.BigDecimal;
import java.time.OffsetDateTime;
import java.time.ZoneOffset;
import java.util.Collections;
import java.util.Optional;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
class SpaceWeatherServiceTest {

    @Mock KpIndexRecordRepository kpRepo;
    @Mock DstIndexRecordRepository dstRepo;
    @Mock SolarWindRecordRepository solarWindRepo;
    @Mock SpaceWeatherAlertRepository alertRepo;

    SpaceWeatherService service;

    @BeforeEach
    void setUp() {
        service = new SpaceWeatherService(kpRepo, dstRepo, solarWindRepo, alertRepo);
    }

    // --- GNSS Quality Classification Tests ---

    @Test
    void classifyQuality_normal() {
        assertEquals(GnssQualityLevel.NORMAL,
                service.classifyQuality(bd("3.0"), bd("-20"), null));
    }

    @Test
    void classifyQuality_caution_byKp() {
        assertEquals(GnssQualityLevel.CAUTION,
                service.classifyQuality(bd("4.5"), bd("-10"), null));
    }

    @Test
    void classifyQuality_caution_byDst() {
        assertEquals(GnssQualityLevel.CAUTION,
                service.classifyQuality(bd("2.0"), bd("-40"), null));
    }

    @Test
    void classifyQuality_degraded_byKp() {
        assertEquals(GnssQualityLevel.DEGRADED,
                service.classifyQuality(bd("5.5"), bd("-10"), null));
    }

    @Test
    void classifyQuality_degraded_byDst() {
        assertEquals(GnssQualityLevel.DEGRADED,
                service.classifyQuality(bd("2.0"), bd("-75"), null));
    }

    @Test
    void classifyQuality_degraded_byGScale() {
        assertEquals(GnssQualityLevel.DEGRADED,
                service.classifyQuality(bd("2.0"), bd("-10"), 2));
    }

    @Test
    void classifyQuality_severe_byKp() {
        assertEquals(GnssQualityLevel.SEVERE,
                service.classifyQuality(bd("7.5"), bd("-10"), null));
    }

    @Test
    void classifyQuality_severe_byDst() {
        assertEquals(GnssQualityLevel.SEVERE,
                service.classifyQuality(bd("2.0"), bd("-120"), null));
    }

    @Test
    void classifyQuality_severe_byGScale() {
        assertEquals(GnssQualityLevel.SEVERE,
                service.classifyQuality(bd("2.0"), bd("-10"), 3));
    }

    @Test
    void classifyQuality_nullValues_returnsNormal() {
        assertEquals(GnssQualityLevel.NORMAL,
                service.classifyQuality(null, null, null));
    }

    @Test
    void classifyQuality_boundaryKp4_isCaution() {
        assertEquals(GnssQualityLevel.CAUTION,
                service.classifyQuality(bd("4.0"), bd("0"), null));
    }

    @Test
    void classifyQuality_boundaryKp5_isDegraded() {
        assertEquals(GnssQualityLevel.DEGRADED,
                service.classifyQuality(bd("5.0"), bd("0"), null));
    }

    @Test
    void classifyQuality_boundaryDstMinus30_isCaution() {
        assertEquals(GnssQualityLevel.CAUTION,
                service.classifyQuality(bd("1.0"), bd("-30"), null));
    }

    @Test
    void classifyQuality_worstConditionWins() {
        // Kp=6 (DEGRADED) + Dst=-120 (SEVERE) → SEVERE wins
        assertEquals(GnssQualityLevel.SEVERE,
                service.classifyQuality(bd("6.0"), bd("-120"), null));
    }

    // --- assessGnssQuality integration ---

    @Test
    void assessGnssQuality_returnsFullResponse() {
        KpIndexRecord kp = new KpIndexRecord();
        kp.setTime(OffsetDateTime.now(ZoneOffset.UTC));
        kp.setKpValue(bd("5.3"));

        DstIndexRecord dst = new DstIndexRecord();
        dst.setTime(OffsetDateTime.now(ZoneOffset.UTC));
        dst.setDstValue(bd("-72"));

        SolarWindRecord sw = new SolarWindRecord();
        sw.setTime(OffsetDateTime.now(ZoneOffset.UTC));
        sw.setWindSpeed(bd("620"));
        sw.setBz(bd("-8.2"));

        when(kpRepo.findTopByOrderByTimeDesc()).thenReturn(Optional.of(kp));
        when(dstRepo.findTopByOrderByTimeDesc()).thenReturn(Optional.of(dst));
        when(solarWindRepo.findTopByOrderByTimeDesc()).thenReturn(Optional.of(sw));
        when(alertRepo.findByAlertTimeAfterOrderByAlertTimeDesc(any())).thenReturn(Collections.emptyList());

        GnssQualityResponse response = service.assessGnssQuality();

        assertEquals(GnssQualityLevel.DEGRADED, response.qualityLevel());
        assertEquals(bd("5.3"), response.kpIndex());
        assertEquals(bd("-72"), response.dstIndex());
        assertEquals(bd("-8.2"), response.bzComponent());
        assertEquals(bd("620"), response.solarWindSpeed());
        assertEquals("FLAG_DISPLACEMENT_DATA", response.recommendation());
        assertNotNull(response.assessment());
    }

    @Test
    void assessGnssQuality_noData_returnsNormal() {
        when(kpRepo.findTopByOrderByTimeDesc()).thenReturn(Optional.empty());
        when(dstRepo.findTopByOrderByTimeDesc()).thenReturn(Optional.empty());
        when(solarWindRepo.findTopByOrderByTimeDesc()).thenReturn(Optional.empty());
        when(alertRepo.findByAlertTimeAfterOrderByAlertTimeDesc(any())).thenReturn(Collections.emptyList());

        GnssQualityResponse response = service.assessGnssQuality();

        assertEquals(GnssQualityLevel.NORMAL, response.qualityLevel());
        assertEquals("NORMAL", response.recommendation());
    }

    private BigDecimal bd(String val) {
        return new BigDecimal(val);
    }
}
