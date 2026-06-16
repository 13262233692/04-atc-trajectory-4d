package com.atc.trajectory4d.model;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.time.Instant;
import java.util.ArrayList;
import java.util.List;

@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class RestrictedAirspace {

    private String id;
    private String name;
    private String reason;
    private RestrictionLevel level;
    private AirspaceShape shape;
    private List<Waypoint> polygonVertices;
    private double minAltitude;
    private double maxAltitude;
    private Instant effectiveFrom;
    private Instant effectiveTo;
    private Instant createdAt;
    private Instant updatedAt;
    private boolean active;

    public enum RestrictionLevel {
        WARNING,
        ADVISORY,
        PROHIBITED
    }

    public enum AirspaceShape {
        POLYGON,
        CYLINDER,
        SPHERE
    }

    public boolean isActiveAtTime(Instant time) {
        if (!active) return false;
        if (effectiveFrom != null && time.isBefore(effectiveFrom)) return false;
        if (effectiveTo != null && time.isAfter(effectiveTo)) return false;
        return true;
    }

    public boolean isAltitudeWithinRange(double altitude) {
        return altitude >= minAltitude && altitude <= maxAltitude;
    }

    public boolean containsPoint(double latitude, double longitude, double altitude, Instant time) {
        if (!isActiveAtTime(time)) return false;
        if (!isAltitudeWithinRange(altitude)) return false;
        return pointInPolygon(latitude, longitude, polygonVertices);
    }

    public static boolean pointInPolygon(double lat, double lon, List<Waypoint> polygon) {
        if (polygon == null || polygon.size() < 3) return false;
        int n = polygon.size();
        boolean inside = false;
        for (int i = 0, j = n - 1; i < n; j = i++) {
            double xi = polygon.get(i).getLatitude();
            double yi = polygon.get(i).getLongitude();
            double xj = polygon.get(j).getLatitude();
            double yj = polygon.get(j).getLongitude();

            if (((yi > lon) != (yj > lon)) &&
                (lat < (xj - xi) * (lon - yi) / (yj - yi) + xi)) {
                inside = !inside;
            }
        }
        return inside;
    }

    public List<Waypoint> getBoundaryPoints() {
        if (polygonVertices == null) return new ArrayList<>();
        return polygonVertices;
    }
}
