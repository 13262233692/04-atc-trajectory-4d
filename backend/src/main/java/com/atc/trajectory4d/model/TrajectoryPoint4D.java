package com.atc.trajectory4d.model;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.time.LocalDateTime;

@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class TrajectoryPoint4D {

    private String flightId;
    private long sequenceNumber;

    private double longitude;
    private double latitude;
    private double altitude;
    private LocalDateTime timestamp;

    private double trueAirspeed;
    private double groundSpeed;
    private double machNumber;

    private double heading;
    private double trackAngle;
    private double verticalSpeed;

    private double mass;
    private double fuelMass;

    private double thrust;
    private double drag;
    private double lift;

    private double fuelFlow;
    private double specificRange;

    private double windSpeed;
    private double windDirection;
    private double temperature;

    private String flightPhase;
    private String nextWaypoint;

    private double distanceToDestination;
    private double timeToDestination;

    public double[] getPositionVector() {
        return new double[]{longitude, latitude, altitude};
    }

    public double[] getVelocityVector() {
        double vx = groundSpeed * Math.cos(Math.toRadians(trackAngle));
        double vy = groundSpeed * Math.sin(Math.toRadians(trackAngle));
        double vz = verticalSpeed;
        return new double[]{vx, vy, vz};
    }

    public static TrajectoryPoint4D interpolate(TrajectoryPoint4D from, TrajectoryPoint4D to, double fraction) {
        if (from == null || to == null) return from;
        if (fraction <= 0) return from;
        if (fraction >= 1) return to;

        LocalDateTime interpolatedTime = from.timestamp.plusSeconds(
                (long) (java.time.Duration.between(from.timestamp, to.timestamp).getSeconds() * fraction)
        );

        return TrajectoryPoint4D.builder()
                .flightId(from.flightId)
                .sequenceNumber(from.sequenceNumber + (long) ((to.sequenceNumber - from.sequenceNumber) * fraction))
                .longitude(from.longitude + (to.longitude - from.longitude) * fraction)
                .latitude(from.latitude + (to.latitude - from.latitude) * fraction)
                .altitude(from.altitude + (to.altitude - from.altitude) * fraction)
                .timestamp(interpolatedTime)
                .trueAirspeed(from.trueAirspeed + (to.trueAirspeed - from.trueAirspeed) * fraction)
                .groundSpeed(from.groundSpeed + (to.groundSpeed - from.groundSpeed) * fraction)
                .machNumber(from.machNumber + (to.machNumber - from.machNumber) * fraction)
                .heading(interpolateAngle(from.heading, to.heading, fraction))
                .trackAngle(interpolateAngle(from.trackAngle, to.trackAngle, fraction))
                .verticalSpeed(from.verticalSpeed + (to.verticalSpeed - from.verticalSpeed) * fraction)
                .mass(from.mass + (to.mass - from.mass) * fraction)
                .fuelMass(from.fuelMass + (to.fuelMass - from.fuelMass) * fraction)
                .thrust(from.thrust + (to.thrust - from.thrust) * fraction)
                .drag(from.drag + (to.drag - from.drag) * fraction)
                .lift(from.lift + (to.lift - from.lift) * fraction)
                .fuelFlow(from.fuelFlow + (to.fuelFlow - from.fuelFlow) * fraction)
                .windSpeed(from.windSpeed + (to.windSpeed - from.windSpeed) * fraction)
                .windDirection(interpolateAngle(from.windDirection, to.windDirection, fraction))
                .temperature(from.temperature + (to.temperature - from.temperature) * fraction)
                .flightPhase(fraction < 0.5 ? from.flightPhase : to.flightPhase)
                .nextWaypoint(to.nextWaypoint)
                .distanceToDestination(to.distanceToDestination + (from.distanceToDestination - to.distanceToDestination) * (1 - fraction))
                .timeToDestination(to.timeToDestination + (from.timeToDestination - to.timeToDestination) * (1 - fraction))
                .build();
    }

    private static double interpolateAngle(double from, double to, double fraction) {
        double delta = to - from;
        if (delta > 180) delta -= 360;
        if (delta < -180) delta += 360;
        return (from + delta * fraction + 360) % 360;
    }

    public String toCsvLine() {
        return String.format("%s,%d,%.6f,%.6f,%.2f,%s,%.2f,%.2f,%.4f,%.2f,%.2f,%.2f,%.2f,%.2f,%.2f,%.2f,%.2f,%.2f,%.2f,%.2f,%.2f,%.2f,%s,%s,%.2f,%.2f",
                flightId, sequenceNumber,
                longitude, latitude, altitude,
                timestamp,
                trueAirspeed, groundSpeed, machNumber,
                heading, trackAngle, verticalSpeed,
                mass, fuelMass,
                thrust, drag, lift,
                fuelFlow, specificRange,
                windSpeed, windDirection, temperature,
                flightPhase, nextWaypoint,
                distanceToDestination, timeToDestination
        );
    }
}
