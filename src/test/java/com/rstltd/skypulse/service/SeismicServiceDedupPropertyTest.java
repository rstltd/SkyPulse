package com.rstltd.skypulse.service;

import com.rstltd.skypulse.domain.seismic.EarthquakeEvent;
import net.jqwik.api.Arbitraries;
import net.jqwik.api.Arbitrary;
import net.jqwik.api.Combinators;
import net.jqwik.api.Example;
import net.jqwik.api.ForAll;
import net.jqwik.api.Property;
import net.jqwik.api.Provide;
import net.jqwik.api.constraints.DoubleRange;
import net.jqwik.api.constraints.IntRange;

import java.math.BigDecimal;
import java.time.OffsetDateTime;
import java.time.ZoneOffset;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * Property-based tests for {@link SeismicService} cross-source de-duplication.
 *
 * <p>The oracles here are deliberately INDEPENDENT of the implementation, so these tests
 * catch a bug even when the same model wrote both the code and the test:
 * <ul>
 *   <li>the 30s / 10km / 0.3-magnitude thresholds come from the documented dedup spec
 *       (CLAUDE.md "Design Decisions"), not from reading the code;</li>
 *   <li>symmetry, idempotence and identical-collapse are mathematical invariants that any
 *       correct "same physical event" relation must satisfy — they assert a relationship,
 *       not a magic number that could be back-filled from the implementation.</li>
 * </ul>
 * See {@code docs/TESTING_STRATEGY.md}.
 */
class SeismicServiceDedupPropertyTest {

    private static final OffsetDateTime BASE =
            OffsetDateTime.of(2026, 3, 1, 12, 0, 0, 0, ZoneOffset.UTC);

    // deduplicateEvents() is pure and never touches the repository, so null is fine here.
    private final SeismicService service = new SeismicService(null);

    // --- Structural invariants (oracle = properties of the relation itself) ---

    @Property
    void dedupSizeIsIndependentOfInputOrder(@ForAll("nearbyPair") List<EarthquakeEvent> pair) {
        int forward = service.deduplicateEvents(List.of(pair.get(0), pair.get(1))).size();
        int reversed = service.deduplicateEvents(List.of(pair.get(1), pair.get(0))).size();
        assertThat(forward)
                .as("dedup result size must not depend on ordering — 'same physical event' is symmetric")
                .isEqualTo(reversed);
    }

    @Property
    void dedupIsIdempotent(@ForAll("events") List<EarthquakeEvent> events) {
        List<EarthquakeEvent> once = service.deduplicateEvents(events);
        List<EarthquakeEvent> twice = service.deduplicateEvents(once);
        assertThat(twice.size())
                .as("dedup(dedup(x)) must equal dedup(x) — dedup is a projection")
                .isEqualTo(once.size());
    }

    @Property
    void identicalCwaAndUsgsCollapseToCwa(
            @ForAll @DoubleRange(min = 22.0, max = 25.0) double lat,
            @ForAll @DoubleRange(min = 120.0, max = 122.0) double lon,
            @ForAll @DoubleRange(min = 4.0, max = 7.0) double mag) {
        EarthquakeEvent cwa = event("CWA-1", BASE, lat, lon, mag, "CWA");
        EarthquakeEvent usgs = event("USGS-1", BASE, lat, lon, mag, "USGS");
        List<EarthquakeEvent> result = service.deduplicateEvents(List.of(usgs, cwa));
        assertThat(result)
                .as("identical time/location/magnitude across sources is one physical event")
                .hasSize(1);
        assertThat(result.get(0).getSource())
                .as("CWA (local authority) is kept over USGS for the same event")
                .isEqualTo("CWA");
    }

    @Property
    void farApartInTimeNeverMerges(
            @ForAll("event") EarthquakeEvent e,
            @ForAll @IntRange(min = 31, max = 86_400) int gapSeconds) {
        EarthquakeEvent later = copyAt(e, e.getTime().plusSeconds(gapSeconds));
        assertThat(service.deduplicateEvents(List.of(e, later)))
                .as("events more than 30s apart are always distinct, whatever their location/magnitude")
                .hasSize(2);
    }

    // --- Boundary matrix on the 30s threshold ---
    // Time is integer-precise, so the '>' vs '>=' boundary is genuinely reachable and killable.
    // (The 10km / 0.3-mag boundaries are floating point: hitting them exactly is not reliably
    //  possible, so those surviving boundary mutants are effectively equivalent and NOT chased.)

    @Example
    void exactlyThirtySecondsApart_isSameEvent() {
        EarthquakeEvent a = event("A", BASE, 23.5, 121.6, 4.6, "CWA");
        EarthquakeEvent b = event("B", BASE.plusSeconds(30), 23.5, 121.6, 4.6, "USGS");
        assertThat(service.deduplicateEvents(List.of(a, b)))
                .as("a time difference exactly at the 30s threshold still counts as one event")
                .hasSize(1);
    }

    @Example
    void thirtyOneSecondsApart_areDistinct() {
        EarthquakeEvent a = event("A", BASE, 23.5, 121.6, 4.6, "CWA");
        EarthquakeEvent b = event("B", BASE.plusSeconds(31), 23.5, 121.6, 4.6, "USGS");
        assertThat(service.deduplicateEvents(List.of(a, b)))
                .as("one second past the 30s threshold must be treated as distinct")
                .hasSize(2);
    }

    // --- generators ---

    @Provide
    Arbitrary<EarthquakeEvent> event() {
        return Combinators.combine(
                        Arbitraries.doubles().between(22.0, 25.0),
                        Arbitraries.doubles().between(120.0, 122.0),
                        Arbitraries.doubles().between(4.0, 7.0),
                        Arbitraries.integers().between(0, 600),
                        Arbitraries.of("CWA", "USGS"))
                .as((lat, lon, mag, sec, src) ->
                        event(src + "-" + sec, BASE.plusSeconds(sec), lat, lon, mag, src));
    }

    @Provide
    Arbitrary<List<EarthquakeEvent>> events() {
        return event().list().ofMaxSize(6);
    }

    /** b is a small perturbation of a so pairs straddle the 30s / 10km / 0.3 thresholds. */
    @Provide
    Arbitrary<List<EarthquakeEvent>> nearbyPair() {
        return Combinators.combine(
                        Arbitraries.doubles().between(22.0, 25.0),
                        Arbitraries.doubles().between(120.0, 122.0),
                        Arbitraries.doubles().between(4.0, 7.0),
                        Arbitraries.integers().between(0, 90),     // dt straddles 30s
                        Arbitraries.doubles().between(0.0, 0.3),   // dLat straddles ~10km
                        Arbitraries.doubles().between(0.0, 0.6))   // dMag straddles 0.3
                .as((lat, lon, mag, dt, dLat, dMag) -> List.of(
                        event("A", BASE, lat, lon, mag, "USGS"),
                        event("B", BASE.plusSeconds(dt), lat + dLat, lon, clampMag(mag + dMag), "USGS")));
    }

    private static double clampMag(double m) {
        return Math.min(9.0, Math.max(0.0, m));
    }

    private static EarthquakeEvent event(String id, OffsetDateTime time,
                                         double lat, double lon, double mag, String source) {
        EarthquakeEvent e = new EarthquakeEvent();
        e.setEventId(id);
        e.setTime(time);
        e.setLatitude(BigDecimal.valueOf(lat));
        e.setLongitude(BigDecimal.valueOf(lon));
        e.setMagnitude(BigDecimal.valueOf(mag));
        e.setSource(source);
        return e;
    }

    private static EarthquakeEvent copyAt(EarthquakeEvent e, OffsetDateTime t) {
        return event(e.getEventId() + "-later", t,
                e.getLatitude().doubleValue(), e.getLongitude().doubleValue(),
                e.getMagnitude().doubleValue(), e.getSource());
    }
}
