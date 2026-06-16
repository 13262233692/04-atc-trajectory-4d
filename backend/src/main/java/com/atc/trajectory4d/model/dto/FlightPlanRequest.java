package com.atc.trajectory4d.model.dto;

import com.atc.trajectory4d.model.Waypoint;
import jakarta.validation.constraints.NotEmpty;
import jakarta.validation.constraints.NotNull;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.time.LocalDateTime;
import java.util.List;

@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class FlightPlanRequest {

    @NotNull(message = "Flight ID is required")
    private String flightId;

    @NotNull(message = "Aircraft type is required")
    private String aircraftType;

    private String airline;
    private String flightNumber;

    @NotNull(message = "Departure airport is required")
    private String departureAirport;

    @NotNull(message = "Arrival airport is required")
    private String arrivalAirport;

    @NotNull(message = "Departure time is required")
    private LocalDateTime departureTime;

    private double initialMass;
    private double fuelMass;
    private double payloadMass;

    private double cruiseAltitude;
    private double cruiseSpeed;
    private double cruiseMach;

    @NotEmpty(message = "At least 2 waypoints are required")
    private List<Waypoint> waypoints;
}
