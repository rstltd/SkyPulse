package com.rstltd.skypulse.backfill;

public record BackfillResult(
        String source,
        String startDate,
        String endDate,
        int totalFetched,
        int inserted,
        int skipped,
        int errors,
        long durationMs,
        String message
) {
    public static BackfillResult success(String source, String startDate, String endDate,
                                         int fetched, int inserted, int skipped, long durationMs) {
        return new BackfillResult(source, startDate, endDate, fetched, inserted, skipped, 0, durationMs,
                String.format("Backfill completed: %d fetched, %d inserted, %d skipped", fetched, inserted, skipped));
    }

    public static BackfillResult error(String source, String startDate, String endDate,
                                        long durationMs, String error) {
        return new BackfillResult(source, startDate, endDate, 0, 0, 0, 1, durationMs, error);
    }
}
