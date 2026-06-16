package com.atc.trajectory4d.model.entity;

import jakarta.persistence.*;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.List;

@Data
@Entity
@Builder
@NoArgsConstructor
@AllArgsConstructor
@Table(name = "flight_plans")
public class FlightPlanEntity {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(name = "flight_id", unique = true, nullable = false, length = 50)
    private String flightId;

    @Column(name = "aircraft_type", nullable = false, length = 50)
    private String aircraftType;

    @Column(name = "airline", length = 100)
    private String airline;

    @Column(name = "flight_number", length = 20)
    private String flightNumber;

    @Column(name = "departure_airport", nullable = false, length = 10)
    private String departureAirport;

    @Column(name = "arrival_airport", nullable = false, length = 10)
    private String arrivalAirport;

    @Column(name = "departure_time", nullable = false)
    private LocalDateTime departureTime;

    @Column(name = "estimated_arrival_time")
    private LocalDateTime estimatedArrivalTime;

    @Column(name = "actual_departure_time")
    private LocalDateTime actualDepartureTime;

    @Column(name = "actual_arrival_time")
    private LocalDateTime actualArrivalTime;

    @Column(name = "initial_mass")
    private double initialMass;

    @Column(name = "fuel_mass")
    private double fuelMass;

    @Column(name = "payload_mass")
    private double payloadMass;

    @Column(name = "cruise_altitude")
    private double cruiseAltitude;

    @Column(name = "cruise_speed")
    private double cruiseSpeed;

    @Column(name = "cruise_mach")
    private double cruiseMach;

    @Column(name = "status", length = 20)
    private String status;

    @Column(name = "created_at")
    private LocalDateTime createdAt;

    @Column(name = "updated_at")
    private LocalDateTime updatedAt;

    @OneToMany(mappedBy = "flightPlan", cascade = CascadeType.ALL, orphanRemoval = true)
    @Builder.Default
    private List<WaypointEntity> waypoints = new ArrayList<>();

    @OneToMany(mappedBy = "flightPlan", cascade = CascadeType.ALL, orphanRemoval = true)
    @Builder.Default
    private List<TrajectoryPointEntity> trajectoryPoints = new ArrayList<>();

    @PrePersist
    protected void onCreate() {
        createdAt = LocalDateTime.now();
        updatedAt = LocalDateTime.now();
    }

    @PreUpdate
    protected void onUpdate() {
        updatedAt = LocalDateTime.now();
    }

    public void addWaypoint(WaypointEntity waypoint) {
        waypoints.add(waypoint);
        waypoint.setFlightPlan(this);
    }

    public void addTrajectoryPoint(TrajectoryPointEntity point) {
        trajectoryPoints.add(point);
        point.setFlightPlan(this);
    }
}
