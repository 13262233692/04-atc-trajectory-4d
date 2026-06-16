package com.atc.trajectory4d.model.entity;

import jakarta.persistence.*;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

@Data
@Entity
@Builder
@NoArgsConstructor
@AllArgsConstructor
@Table(name = "waypoints")
public class WaypointEntity {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "flight_plan_id", nullable = false)
    private FlightPlanEntity flightPlan;

    @Column(name = "sequence_number", nullable = false)
    private int sequenceNumber;

    @Column(name = "name", nullable = false, length = 50)
    private String name;

    @Column(name = "latitude", nullable = false)
    private double latitude;

    @Column(name = "longitude", nullable = false)
    private double longitude;

    @Column(name = "altitude")
    private Double altitude;

    @Column(name = "speed")
    private Double speed;

    @Column(name = "estimated_time")
    private Double estimatedTime;

    @Column(name = "is_departure")
    private boolean isDeparture;

    @Column(name = "is_destination")
    private boolean isDestination;

    @Column(name = "created_at")
    private java.time.LocalDateTime createdAt;

    @PrePersist
    protected void onCreate() {
        createdAt = java.time.LocalDateTime.now();
    }
}
