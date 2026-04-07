package com.rstltd.skypulse.service;

import com.rstltd.skypulse.domain.hydrology.ReservoirStatus;
import com.rstltd.skypulse.domain.hydrology.WaterLevelObservation;
import com.rstltd.skypulse.repository.ReservoirStatusRepository;
import com.rstltd.skypulse.repository.WaterLevelObservationRepository;
import com.rstltd.skypulse.util.TimeUtils;
import org.springframework.stereotype.Service;

import java.time.OffsetDateTime;
import java.util.Comparator;
import java.util.List;
import java.util.Map;

@Service
public class HydrologyService {

    private final WaterLevelObservationRepository waterLevelRepo;
    private final ReservoirStatusRepository reservoirRepo;

    /** Reservoir IDs ordered north to south by geographic location */
    private static final Map<String, Integer> RESERVOIR_ORDER = Map.ofEntries(
            // North — 基隆/新北/臺北
            Map.entry("10204", 1),   // 新山水庫
            Map.entry("10205", 2),   // 翡翠水庫
            Map.entry("10212", 3),   // 直潭壩
            Map.entry("10211", 4),   // 青潭堰
            Map.entry("10203", 5),   // 西勢水庫
            // 桃園
            Map.entry("10201", 10),  // 石門水庫
            // 新竹
            Map.entry("10401", 20),  // 寶山水庫
            Map.entry("10405", 21),  // 寶山第二水庫
            // 苗栗
            Map.entry("10501", 30),  // 永和山水庫
            Map.entry("10601", 31),  // 明德水庫
            Map.entry("10503", 32),  // 大埔水庫
            Map.entry("20101", 33),  // 鯉魚潭水庫
            // 臺中
            Map.entry("20201", 40),  // 德基水庫
            Map.entry("20202", 41),  // 石岡壩
            // 南投
            Map.entry("20501", 50),  // 霧社水庫
            Map.entry("20502", 51),  // 日月潭水庫
            Map.entry("20503", 52),  // 集集攔河堰
            Map.entry("20509", 53),  // 湖山水庫
            // 嘉義
            Map.entry("30301", 60),  // 仁義潭水庫
            Map.entry("30302", 61),  // 蘭潭水庫
            // 臺南
            Map.entry("30401", 70),  // 白河水庫
            Map.entry("30403", 71),  // 德元埤水庫
            Map.entry("30501", 72),  // 烏山頭水庫
            Map.entry("30502", 73),  // 曾文水庫
            Map.entry("30503", 74),  // 南化水庫
            Map.entry("30504", 75),  // 鏡面水庫
            Map.entry("30601", 76),  // 虎頭埤水庫
            Map.entry("30602", 77),  // 鹽水埤水庫
            // 高雄
            Map.entry("30801", 80),  // 澄清湖水庫
            Map.entry("30802", 81),  // 阿公店水庫
            Map.entry("31002", 82),  // 甲仙攔河堰
            // 屏東
            Map.entry("31201", 90),  // 牡丹水庫
            // 臺東
            Map.entry("31301", 95)   // 成功水庫
    );

    public HydrologyService(WaterLevelObservationRepository waterLevelRepo,
                            ReservoirStatusRepository reservoirRepo) {
        this.waterLevelRepo = waterLevelRepo;
        this.reservoirRepo = reservoirRepo;
    }

    public List<WaterLevelObservation> getLatestWaterLevels() {
        OffsetDateTime now = TimeUtils.nowUtc();
        return waterLevelRepo.findByTimeBetween(now.minusHours(2), now);
    }

    public List<WaterLevelObservation> getWaterLevelByStation(String stationCode, int hours) {
        OffsetDateTime now = TimeUtils.nowUtc();
        return waterLevelRepo.findByStationCodeAndTimeBetweenOrderByTimeAsc(
                stationCode, now.minusHours(hours), now);
    }

    public List<ReservoirStatus> getLatestReservoirStatus() {
        OffsetDateTime now = TimeUtils.nowUtc();
        // Use 25-hour window to include reservoirs that report only once daily
        List<ReservoirStatus> latest = reservoirRepo.findLatestPerReservoir(
                now.minusHours(25), now);
        return latest.stream()
                .filter(r -> RESERVOIR_ORDER.containsKey(r.getReservoirId()))
                .sorted(Comparator.comparingInt(r ->
                        RESERVOIR_ORDER.get(r.getReservoirId())))
                .toList();
    }
}
