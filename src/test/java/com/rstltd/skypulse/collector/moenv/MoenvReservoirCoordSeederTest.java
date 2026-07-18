package com.rstltd.skypulse.collector.moenv;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.rstltd.skypulse.domain.hydrology.Reservoir;
import com.rstltd.skypulse.repository.ReservoirRepository;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.test.util.ReflectionTestUtils;
import reactor.core.publisher.Mono;

import java.math.BigDecimal;
import java.util.List;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
class MoenvReservoirCoordSeederTest {

    @Mock MoenvApiClient moenvApiClient;
    @Mock ReservoirRepository reservoirRepo;

    MoenvReservoirCoordSeeder seeder;

    // Bare-array shape of GISEPA_P_27 (field spelling latitute/longitute is the API's own).
    static final String JSON = """
            [{"dam":"石門水庫","countyname":"桃園市","latitute":"24.813611","longitute":"121.242222"},
             {"dam":"翡翠水庫","countyname":"新北市","latitute":"24.910000","longitute":"121.580000"}]
            """;

    @BeforeEach
    void setUp() {
        seeder = new MoenvReservoirCoordSeeder(moenvApiClient, reservoirRepo, new ObjectMapper());
        ReflectionTestUtils.setField(seeder, "apiKey", "test-key");
        ReflectionTestUtils.setField(seeder, "reservoirDataset", "GISEPA_P_27");
    }

    private static Reservoir reservoir(String id, String name) {
        Reservoir r = new Reservoir();
        r.setReservoirId(id);
        r.setReservoirName(name);
        return r;
    }

    @Test
    void seed_matchesByDamName_setsCoordsAndCounty_leavesUnmatched() {
        Reservoir shimen = reservoir("10201", "石門水庫");   // matches
        Reservoir hushan = reservoir("20509", "湖山水庫");   // no GISEPA dam
        when(moenvApiClient.getDataset(eq("GISEPA_P_27"), eq("test-key"))).thenReturn(Mono.just(JSON));
        when(reservoirRepo.findAll()).thenReturn(List.of(shimen, hushan));
        when(reservoirRepo.saveAll(anyList())).thenAnswer(i -> i.getArgument(0));

        int updated = seeder.seed();

        assertEquals(1, updated);
        @SuppressWarnings("unchecked")
        ArgumentCaptor<List<Reservoir>> captor = ArgumentCaptor.forClass(List.class);
        verify(reservoirRepo).saveAll(captor.capture());
        List<Reservoir> saved = captor.getValue();
        assertEquals(1, saved.size());
        assertEquals("石門水庫", saved.get(0).getReservoirName());
        assertEquals(0, new BigDecimal("24.813611").compareTo(saved.get(0).getLatitude()));
        assertEquals(0, new BigDecimal("121.242222").compareTo(saved.get(0).getLongitude()));
        assertEquals("桃園市", saved.get(0).getCounty());
        // Unmatched reservoir untouched.
        assertNull(hushan.getLatitude());
    }

    @Test
    void seed_blankApiKey_skips() {
        ReflectionTestUtils.setField(seeder, "apiKey", "");

        int updated = seeder.seed();

        assertEquals(0, updated);
        verifyNoInteractions(moenvApiClient);
        verify(reservoirRepo, never()).saveAll(anyList());
    }
}
