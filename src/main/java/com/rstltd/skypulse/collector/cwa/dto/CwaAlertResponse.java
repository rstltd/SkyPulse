package com.rstltd.skypulse.collector.cwa.dto;

import com.fasterxml.jackson.annotation.JsonIgnoreProperties;
import com.fasterxml.jackson.annotation.JsonProperty;

import java.util.List;

@JsonIgnoreProperties(ignoreUnknown = true)
public record CwaAlertResponse(
        String success,
        Result result
) {
    @JsonIgnoreProperties(ignoreUnknown = true)
    public record Result(
            @JsonProperty("resource_id") String resourceId,
            Records records
    ) {}

    @JsonIgnoreProperties(ignoreUnknown = true)
    public record Records(List<AlertRecord> record) {}

    @JsonIgnoreProperties(ignoreUnknown = true)
    public record AlertRecord(
            String datasetDescription,
            String datasetLanguage,
            String issueTime,
            String startTime,
            String endTime,
            String update,
            String contentText,
            String phenomena,
            String significance,
            String locationName
    ) {}
}
