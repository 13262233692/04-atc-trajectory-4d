package com.atc.trajectory4d.model.entity;

import jakarta.persistence.*;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.time.LocalDateTime;

@Data
@Entity
@Builder
@NoArgsConstructor
@AllArgsConstructor
@Table(name = "trajectory_points", indexes = {
        @Index(name = "idx_flight_id", columnList = "flight_plan_id"),
        @Index(name = "idx_sequence", columnList = "flight_plan_id, sequence_number"),
        @Index(name = "idx_timestamp", columnList = "timestamp")
})
public class TrajectoryPointEntity {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "flight_plan_id", nullable = false)
    private FlightPlanEntity flightPlan;

    @Column(name = "flight_id", length = 50)
    private String flightId;

    @Column(name = "sequence_number", nullable = false)
    private long sequenceNumber;

    @Column(name = "longitude", nullable = false)
    private double longitude;

    @Column(name = "latitude", nullable = false)
    private double latitude;

    @Column(name = "altitude", nullable = false)
    private double altitude;

    @Column(name = "timestamp", nullable = false)
    private LocalDateTime timestamp;

    @Column(name = "true_airspeed")
    private double trueAirspeed;

    @Column(name = "ground_speed")
    private double groundSpeed;

    @Column(name = "mach_number")
    private double machNumber;

    @Column(name = "heading")
    private double heading;

    @Column(name = "track_angle")
    private double trackAngle;

    @Column(name = "vertical_speed")
    private double verticalSpeed;

    @Column(name = "mass")
    private double mass;

    @Column(name = "fuel_mass")
    private double fuelMass;

    @Column(name = "thrust")
    private double thrust;

    @Column(name = "drag")
    private double drag;

    @Column(name = "lift")
    private double lift;

    @Column(name = "fuel_flow")
    private double fuelFlow;

    @Column(name = "specific_range")
    private double specificRange;

    @Column(name = "wind_speed")
    private double windSpeed;

    @Column(name = "wind_direction")
    private double windDirection;

    @Column(name = "temperature")
    private double temperature;

    @Column(name = "flight_phase", length = 20)
    private String flightPhase;

    @Column(name = "next_waypoint", length = 50)
    private String nextWaypoint;

    @Column(name = "distance_to_destination")
    private double distanceToDestination;

    @Column(name = "time_to_destination")
    private double timeToDestination;

    @Column(name = "created_at")
    private LocalDateTime createdAt;

    @PrePersist
    protected void onCreate() {
        createdAt = LocalDateTime.now();
    }
}
