package com.atc.trajectory4d.model.entity;

import com.atc.trajectory4d.model.RestrictedAirspace;
import jakarta.persistence.*;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;
import org.hibernate.annotations.JdbcTypeCode;
import org.hibernate.type.SqlTypes;

import java.time.Instant;
import java.util.Map;

@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
@Entity
@Table(name = "restricted_airspaces", indexes = {
    @Index(name = "idx_airspace_active", columnList = "active"),
    @Index(name = "idx_airspace_time", columnList = "effective_from, effective_to")
})
public class RestrictedAirspaceEntity {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(name = "airspace_id", unique = true, nullable = false, length = 100)
    private String airspaceId;

    @Column(nullable = false, length = 200)
    private String name;

    @Column(length = 500)
    private String reason;

    @Enumerated(EnumType.STRING)
    @Column(name = "level", nullable = false, length = 30)
    private RestrictedAirspace.RestrictionLevel level;

    @Enumerated(EnumType.STRING)
    @Column(name = "shape", nullable = false, length = 30)
    private RestrictedAirspace.AirspaceShape shape;

    @JdbcTypeCode(SqlTypes.JSON)
    @Column(name = "polygon_vertices", columnDefinition = "json")
    private Map<String, Object> polygonVertices;

    @Column(name = "min_altitude")
    private Double minAltitude;

    @Column(name = "max_altitude")
    private Double maxAltitude;

    @Column(name = "effective_from")
    private Instant effectiveFrom;

    @Column(name = "effective_to")
    private Instant effectiveTo;

    @Column(nullable = false)
    private Boolean active = true;

    @Column(name = "created_at")
    private Instant createdAt;

    @Column(name = "updated_at")
    private Instant updatedAt;

    @PrePersist
    protected void onCreate() {
        createdAt = Instant.now();
        updatedAt = Instant.now();
    }

    @PreUpdate
    protected void onUpdate() {
        updatedAt = Instant.now();
    }
}
