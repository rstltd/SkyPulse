package com.rstltd.skypulse.domain.spaceweather;

import com.fasterxml.jackson.annotation.JsonIgnore;
import jakarta.persistence.*;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;
import org.hibernate.annotations.JdbcTypeCode;
import org.hibernate.type.SqlTypes;

import java.math.BigDecimal;
import java.time.OffsetDateTime;

@Entity
@Table(name = "kp_index_records")
@Getter @Setter @NoArgsConstructor
public class KpIndexRecord {

    @Id
    @Column(nullable = false)
    private OffsetDateTime time;

    @Column(name = "kp_value", nullable = false, precision = 3, scale = 1)
    private BigDecimal kpValue;

    @Column(length = 20)
    private String source;

    @JsonIgnore
    @JdbcTypeCode(SqlTypes.JSON)
    @Column(name = "raw_data", columnDefinition = "jsonb")
    private String rawData;

    @JsonIgnore
    @Column(name = "created_at", updatable = false, insertable = false)
    private OffsetDateTime createdAt;
}
