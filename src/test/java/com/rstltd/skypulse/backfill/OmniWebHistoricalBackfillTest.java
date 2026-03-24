package com.rstltd.skypulse.backfill;

import com.rstltd.skypulse.backfill.OmniWebClient.OmniWebRecord;
import com.rstltd.skypulse.repository.DstIndexRecordRepository;
import com.rstltd.skypulse.repository.KpIndexRecordRepository;
import com.rstltd.skypulse.repository.SolarWindRecordRepository;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.time.LocalDate;
import java.time.OffsetDateTime;
import java.util.List;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
class OmniWebHistoricalBackfillTest {

    @Mock OmniWebClient omniWebClient;
    @Mock KpIndexRecordRepository kpRepo;
    @Mock DstIndexRecordRepository dstRepo;
    @Mock SolarWindRecordRepository solarWindRepo;

    OmniWebHistoricalBackfill backfill;

    @BeforeEach
    void setUp() {
        backfill = new OmniWebHistoricalBackfill(omniWebClient, kpRepo, dstRepo, solarWindRepo);
    }

    @Test
    void execute_insertsKpDstAndSolarWind() {
        var records = List.of(
                new OmniWebRecord(2025, 1, 0, 427.0, 6.66, -3.4, 40, -26),
                new OmniWebRecord(2025, 1, 1, 446.0, 6.07, -4.9, 53, -30)
        );
        when(omniWebClient.query(any(LocalDate.class), any(LocalDate.class))).thenReturn(records);
        when(kpRepo.existsById(any(OffsetDateTime.class))).thenReturn(false);
        when(dstRepo.existsById(any(OffsetDateTime.class))).thenReturn(false);
        when(solarWindRepo.existsById(any(OffsetDateTime.class))).thenReturn(false);
        when(kpRepo.save(any())).thenAnswer(i -> i.getArgument(0));
        when(dstRepo.save(any())).thenAnswer(i -> i.getArgument(0));
        when(solarWindRepo.save(any())).thenAnswer(i -> i.getArgument(0));

        BackfillResult result = backfill.execute(
                LocalDate.of(2025, 1, 1), LocalDate.of(2025, 1, 31));

        assertEquals(2, result.totalFetched());
        // 2 Kp + 2 Dst + 2 SW = 6 inserts
        assertEquals(6, result.inserted());
        assertEquals(0, result.skipped());
        verify(kpRepo, times(2)).save(argThat(kp -> kp.getSource().equals("OMNIWEB")));
        verify(dstRepo, times(2)).save(any());
        verify(solarWindRepo, times(2)).save(any());
    }

    @Test
    void execute_skipsDuplicates() {
        var records = List.of(
                new OmniWebRecord(2025, 1, 0, 427.0, 6.66, -3.4, 40, -26)
        );
        when(omniWebClient.query(any(), any())).thenReturn(records);
        when(kpRepo.existsById(any())).thenReturn(true);
        when(dstRepo.existsById(any())).thenReturn(true);
        when(solarWindRepo.existsById(any())).thenReturn(true);

        BackfillResult result = backfill.execute(
                LocalDate.of(2025, 1, 1), LocalDate.of(2025, 1, 31));

        assertEquals(1, result.totalFetched());
        assertEquals(0, result.inserted());
        assertEquals(3, result.skipped()); // 1 kp + 1 dst + 1 sw all skipped
    }

    @Test
    void execute_handlesNullValues() {
        // OMNIWeb fill values parsed as null
        var records = List.of(
                new OmniWebRecord(2025, 1, 0, null, null, null, null, null)
        );
        when(omniWebClient.query(any(), any())).thenReturn(records);

        BackfillResult result = backfill.execute(
                LocalDate.of(2025, 1, 1), LocalDate.of(2025, 1, 31));

        assertEquals(1, result.totalFetched());
        assertEquals(0, result.inserted()); // All null, nothing to insert
    }

    // --- OmniWebClient parser tests ---

    @Test
    void parseResponse_parsesFixedWidthData() {
        OmniWebClient client = new OmniWebClient(null); // parser doesn't need WebClient
        String response = """
                <HTML><BODY>
                <pre>Selected parameters:
                 1 SW Plasma Speed, km/s
                 2 Flow pressure
                 3 BZ, nT (GSE)
                 4 Kp index
                 5 Dst-index, nT

                YEAR DOY HR    1     2     3  4     5\s
                2025   1  0  427.  6.66  -3.4 40   -26
                2025   1  1  446.  6.07  -4.9 53   -30
                2025   1  2 9999. 999.9 999.9 99 99999
                </pre></BODY></HTML>
                """;

        List<OmniWebRecord> records = client.parseResponse(response);

        assertEquals(3, records.size());

        // Normal values
        assertEquals(2025, records.get(0).year());
        assertEquals(1, records.get(0).doy());
        assertEquals(0, records.get(0).hour());
        assertEquals(427.0, records.get(0).windSpeed());
        assertEquals(-3.4, records.get(0).bz());
        assertEquals(40, records.get(0).kpTenths());
        assertEquals(-26, records.get(0).dst());

        // Second record
        assertEquals(53, records.get(1).kpTenths());
        assertEquals(-30, records.get(1).dst());

        // Fill values → null
        assertNull(records.get(2).windSpeed());
        assertNull(records.get(2).bz());
        assertNull(records.get(2).kpTenths());
        assertNull(records.get(2).dst());
    }

    @Test
    void parseResponse_emptyResponse() {
        OmniWebClient client = new OmniWebClient(null);
        List<OmniWebRecord> records = client.parseResponse("<HTML><BODY></BODY></HTML>");
        assertTrue(records.isEmpty());
    }
}
