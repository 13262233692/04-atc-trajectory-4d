package com.atc.trajectory4d.trajectory;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class FlightStateVector {

    private double longitude;
    private double latitude;
    private double altitude;

    private double trueAirspeed;
    private double heading;
    private double verticalSpeed;

    private double mass;
    private double fuelMass;

    public double[] toArray() {
        return new double[]{
                longitude,
                latitude,
                altitude,
                trueAirspeed,
                heading,
                verticalSpeed,
                mass,
                fuelMass
        };
    }

    public static FlightStateVector fromArray(double[] state) {
        if (state == null || state.length < 8) {
            throw new IllegalArgumentException("State array must have at least 8 elements");
        }
        return FlightStateVector.builder()
                .longitude(state[0])
                .latitude(state[1])
                .altitude(state[2])
                .trueAirspeed(state[3])
                .heading(state[4])
                .verticalSpeed(state[5])
                .mass(state[6])
                .fuelMass(state[7])
                .build();
    }

    public FlightStateVector add(FlightStateVector other) {
        return FlightStateVector.builder()
                .longitude(this.longitude + other.longitude)
                .latitude(this.latitude + other.latitude)
                .altitude(this.altitude + other.altitude)
                .trueAirspeed(this.trueAirspeed + other.trueAirspeed)
                .heading((this.heading + other.heading + 360) % 360)
                .verticalSpeed(this.verticalSpeed + other.verticalSpeed)
                .mass(this.mass + other.mass)
                .fuelMass(this.fuelMass + other.fuelMass)
                .build();
    }

    public FlightStateVector scale(double factor) {
        return FlightStateVector.builder()
                .longitude(this.longitude * factor)
                .latitude(this.latitude * factor)
                .altitude(this.altitude * factor)
                .trueAirspeed(this.trueAirspeed * factor)
                .heading(this.heading * factor)
                .verticalSpeed(this.verticalSpeed * factor)
                .mass(this.mass * factor)
                .fuelMass(this.fuelMass * factor)
                .build();
    }

    public FlightStateVector copy() {
        return FlightStateVector.builder()
                .longitude(this.longitude)
                .latitude(this.latitude)
                .altitude(this.altitude)
                .trueAirspeed(this.trueAirspeed)
                .heading(this.heading)
                .verticalSpeed(this.verticalSpeed)
                .mass(this.mass)
                .fuelMass(this.fuelMass)
                .build();
    }
}
