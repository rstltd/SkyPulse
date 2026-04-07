package com.rstltd.skypulse.domain.log;

import jakarta.persistence.*;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

import java.time.OffsetDateTime;

@Entity
@Table(name = "system_logs")
@Getter @Setter @NoArgsConstructor
public class SystemLog {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(nullable = false)
    private OffsetDateTime time;

    @Column(nullable = false, length = 20)
    private String category;

    @Column(nullable = false, length = 10)
    private String level;

    @Column(nullable = false, length = 50)
    private String source;

    @Column(length = 500)
    private String message;

    @Column(name = "fetched_count")
    private Integer fetchedCount;

    @Column(name = "valid_count")
    private Integer validCount;

    @Column(name = "persisted_count")
    private Integer persistedCount;

    @Column(name = "duration_ms")
    private Long durationMs;

    @Column(name = "error_detail", columnDefinition = "TEXT")
    private String errorDetail;

    @Column(name = "created_at", updatable = false, insertable = false)
    private OffsetDateTime createdAt;
}
