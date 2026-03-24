package com.rstltd.skypulse.collector.common;

public record CollectorResult(
        String source,
        Status status,
        int fetchedCount,
        int validCount,
        int persistedCount,
        long durationMs,
        String errorMessage
) {
    public enum Status { SUCCESS, EMPTY, FAILURE }

    public static CollectorResult success(String source, int persisted,
                                          int fetched, int valid, long durationMs) {
        return new CollectorResult(source, Status.SUCCESS, fetched, valid, persisted, durationMs, null);
    }

    public static CollectorResult empty(String source, long durationMs) {
        return new CollectorResult(source, Status.EMPTY, 0, 0, 0, durationMs, null);
    }

    public static CollectorResult failure(String source, long durationMs, String error) {
        return new CollectorResult(source, Status.FAILURE, 0, 0, 0, durationMs, error);
    }

    public boolean isSuccess() {
        return status == Status.SUCCESS;
    }
}
