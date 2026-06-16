package com.atc.trajectory4d.model;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class Waypoint {

    private String name;
    private double latitude;
    private double longitude;
    private Double altitude;
    private Double speed;
    private Double estimatedTime;

    private boolean isDeparture;
    private boolean isDestination;

    public double distanceTo(Waypoint other) {
        return calculateGreatCircleDistance(
                this.latitude, this.longitude,
                other.latitude, other.longitude
        );
    }

    public double bearingTo(Waypoint other) {
        return calculateBearing(
                this.latitude, this.longitude,
                other.latitude, other.longitude
        );
    }

    public static double calculateGreatCircleDistance(double lat1, double lon1, double lat2, double lon2) {
        double R = 6371000.0;
        double lat1Rad = Math.toRadians(lat1);
        double lat2Rad = Math.toRadians(lat2);
        double deltaLat = Math.toRadians(lat2 - lat1);
        double deltaLon = Math.toRadians(lon2 - lon1);

        double a = Math.sin(deltaLat / 2) * Math.sin(deltaLat / 2)
                + Math.cos(lat1Rad) * Math.cos(lat2Rad)
                * Math.sin(deltaLon / 2) * Math.sin(deltaLon / 2);
        double c = 2 * Math.atan2(Math.sqrt(a), Math.sqrt(1 - a));

        return R * c;
    }

    public static double calculateBearing(double lat1, double lon1, double lat2, double lon2) {
        double lat1Rad = Math.toRadians(lat1);
        double lat2Rad = Math.toRadians(lat2);
        double deltaLon = Math.toRadians(lon2 - lon1);

        double y = Math.sin(deltaLon) * Math.cos(lat2Rad);
        double x = Math.cos(lat1Rad) * Math.sin(lat2Rad)
                - Math.sin(lat1Rad) * Math.cos(lat2Rad) * Math.cos(deltaLon);

        double bearing = Math.toDegrees(Math.atan2(y, x));
        return (bearing + 360) % 360;
    }

    public static Waypoint interpolate(Waypoint from, Waypoint to, double fraction) {
        return Waypoint.builder()
                .name(from.name + "_to_" + to.name + "_" + Math.round(fraction * 100))
                .latitude(from.latitude + (to.latitude - from.latitude) * fraction)
                .longitude(from.longitude + (to.longitude - from.longitude) * fraction)
                .altitude(from.altitude != null && to.altitude != null
                        ? from.altitude + (to.altitude - from.altitude) * fraction : null)
                .speed(from.speed != null && to.speed != null
                        ? from.speed + (to.speed - from.speed) * fraction : null)
                .build();
    }
}
