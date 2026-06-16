package com.atc.trajectory4d.model;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.List;

@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class FlightPlan {

    private String flightId;
    private String aircraftType;
    private String airline;
    private String flightNumber;

    private String departureAirport;
    private String arrivalAirport;

    private LocalDateTime departureTime;
    private LocalDateTime estimatedArrivalTime;

    private double initialMass;
    private double fuelMass;
    private double payloadMass;

    private double cruiseAltitude;
    private double cruiseSpeed;
    private double cruiseMach;

    @Builder.Default
    private List<Waypoint> waypoints = new ArrayList<>();

    public Waypoint getDepartureWaypoint() {
        return waypoints.stream()
                .filter(Waypoint::isDeparture)
                .findFirst()
                .orElse(waypoints.isEmpty() ? null : waypoints.get(0));
    }

    public Waypoint getArrivalWaypoint() {
        return waypoints.stream()
                .filter(Waypoint::isDestination)
                .findFirst()
                .orElse(waypoints.isEmpty() ? null : waypoints.get(waypoints.size() - 1));
    }

    public double getTotalDistance() {
        if (waypoints.size() < 2) return 0.0;
        double total = 0.0;
        for (int i = 0; i < waypoints.size() - 1; i++) {
            total += waypoints.get(i).distanceTo(waypoints.get(i + 1));
        }
        return total;
    }

    public double getEstimatedFlightTime() {
        if (cruiseSpeed <= 0) return 0.0;
        return getTotalDistance() / cruiseSpeed;
    }

    public void addWaypoint(Waypoint waypoint) {
        if (waypoints == null) {
            waypoints = new ArrayList<>();
        }
        waypoints.add(waypoint);
    }
}
