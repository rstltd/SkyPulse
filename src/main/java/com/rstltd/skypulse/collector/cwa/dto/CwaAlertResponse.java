package com.rstltd.skypulse.collector.cwa.dto;

import com.fasterxml.jackson.annotation.JsonIgnoreProperties;

import java.util.List;

@JsonIgnoreProperties(ignoreUnknown = true)
public record CwaAlertResponse(
        String success,
        Records records
) {

    @JsonIgnoreProperties(ignoreUnknown = true)
    public record Records(List<AlertRecord> record) {}

    @JsonIgnoreProperties(ignoreUnknown = true)
    public record AlertRecord(
            DatasetInfo datasetInfo,
            Contents contents,
            HazardConditions hazardConditions
    ) {}

    @JsonIgnoreProperties(ignoreUnknown = true)
    public record DatasetInfo(
            String datasetDescription,
            String datasetLanguage,
            ValidTime validTime,
            String issueTime,
            String update
    ) {}

    @JsonIgnoreProperties(ignoreUnknown = true)
    public record ValidTime(
            String startTime,
            String endTime
    ) {}

    @JsonIgnoreProperties(ignoreUnknown = true)
    public record Contents(Content content) {}

    @JsonIgnoreProperties(ignoreUnknown = true)
    public record Content(
            String contentLanguage,
            String contentText
    ) {}

    @JsonIgnoreProperties(ignoreUnknown = true)
    public record HazardConditions(Hazards hazards) {}

    @JsonIgnoreProperties(ignoreUnknown = true)
    public record Hazards(List<Hazard> hazard) {}

    @JsonIgnoreProperties(ignoreUnknown = true)
    public record Hazard(HazardInfo info) {}

    @JsonIgnoreProperties(ignoreUnknown = true)
    public record HazardInfo(
            String language,
            String phenomena,
            String significance,
            AffectedAreas affectedAreas
    ) {}

    @JsonIgnoreProperties(ignoreUnknown = true)
    public record AffectedAreas(List<Location> location) {}

    @JsonIgnoreProperties(ignoreUnknown = true)
    public record Location(String locationName) {}
}
