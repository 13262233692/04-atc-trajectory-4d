package com.atc.trajectory4d.avoidance;

import com.atc.trajectory4d.model.TrajectoryPoint4D;
import com.atc.trajectory4d.model.Waypoint;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Component;

import java.time.Instant;
import java.util.ArrayList;
import java.util.List;

@Slf4j
@Component
public class TrajectorySmoother {

    public List<Waypoint> smoothPath(List<Waypoint> waypoints, int iterations) {
        if (waypoints == null || waypoints.size() < 3) return waypoints;

        List<Waypoint> current = new ArrayList<>(waypoints);

        for (int iter = 0; iter < iterations; iter++) {
            List<Waypoint> next = new ArrayList<>();
            next.add(current.get(0));

            for (int i = 1; i < current.size() - 1; i++) {
                Waypoint prev = current.get(i - 1);
                Waypoint curr = current.get(i);
                Waypoint nextWp = current.get(i + 1);

                double lat = (prev.getLatitude() + 2 * curr.getLatitude() + nextWp.getLatitude()) / 4.0;
                double lon = (prev.getLongitude() + 2 * curr.getLongitude() + nextWp.getLongitude()) / 4.0;

                double alt1 = safeAlt(prev);
                double alt2 = safeAlt(curr);
                double alt3 = safeAlt(nextWp);
                double alt = (alt1 + 2 * alt2 + alt3) / 4.0;

                Waypoint smoothed = Waypoint.builder()
                        .name(curr.getName())
                        .latitude(lat)
                        .longitude(lon)
                        .altitude(alt)
                        .speed(curr.getSpeed())
                        .build();

                next.add(smoothed);
            }
            next.add(current.get(current.size() - 1));
            current = next;
        }

        return densifyPath(current, 50000);
    }

    private List<Waypoint> densifyPath(List<Waypoint> waypoints, double maxSegmentLength) {
        List<Waypoint> result = new ArrayList<>();
        result.add(waypoints.get(0));

        for (int i = 1; i < waypoints.size(); i++) {
            Waypoint from = waypoints.get(i - 1);
            Waypoint to = waypoints.get(i);
            double dist = from.distanceTo(to);

            if (dist <= maxSegmentLength) {
                result.add(to);
                continue;
            }

            int steps = (int) Math.ceil(dist / maxSegmentLength);
            for (int s = 1; s <= steps; s++) {
                double frac = s / (double) steps;
                Waypoint interp = Waypoint.interpolate(from, to, frac);
                interp.setName(from.getName() + "_i" + s);
                result.add(interp);
            }
        }
        return result;
    }

    public List<TrajectoryPoint4D> blendTrajectories(
            List<TrajectoryPoint4D> originalPoints,
            List<Waypoint> detourWaypoints,
            int currentIndex,
            double transitionTimeSeconds,
            double airspeed) {

        if (originalPoints == null || originalPoints.isEmpty()) {
            return build4DFromWaypoints(detourWaypoints, airspeed, Instant.now());
        }

        if (currentIndex < 0) currentIndex = 0;
        if (currentIndex >= originalPoints.size()) currentIndex = originalPoints.size() - 1;

        TrajectoryPoint4D blendStart = originalPoints.get(currentIndex);

        List<TrajectoryPoint4D> result = new ArrayList<>();
        for (int i = 0; i <= currentIndex; i++) {
            result.add(originalPoints.get(i));
        }

        int blendSteps = (int) Math.max(5, transitionTimeSeconds / 5.0);
        if (!detourWaypoints.isEmpty()) {
            Waypoint firstDetour = detourWaypoints.get(0);

            double startLat = blendStart.getLatitude();
            double startLon = blendStart.getLongitude();
            double startAlt = blendStart.getAltitude();
            double endLat = firstDetour.getLatitude();
            double endLon = firstDetour.getLongitude();
            double endAlt = firstDetour.getAltitude() != null ? firstDetour.getAltitude() : startAlt;

            Instant startTime = blendStart.getTimestamp() != null
                    ? blendStart.getTimestamp() : Instant.now();

            for (int s = 1; s <= blendSteps; s++) {
                double frac = s / (double) blendSteps;
                double smooth = frac * frac * (3 - 2 * frac);

                TrajectoryPoint4D pt = new TrajectoryPoint4D();
                pt.setFlightId(blendStart.getFlightId());
                pt.setSequenceNumber((long) (result.size() + s));
                pt.setLatitude(startLat + (endLat - startLat) * smooth);
                pt.setLongitude(startLon + (endLon - startLon) * smooth);
                pt.setAltitude(startAlt + (endAlt - startAlt) * smooth);
                pt.setTimestamp(startTime.plusSeconds((long) (s * 5)));
                pt.setGroundSpeed(airspeed);
                pt.setTrueAirspeed(airspeed);
                pt.setHeading(blendStart.getHeading());
                pt.setFlightPhase(blendStart.getFlightPhase());
                pt.setMass(blendStart.getMass());
                pt.setFuelMass(blendStart.getFuelMass());
                result.add(pt);
            }
        }

        Instant lastTime = result.get(result.size() - 1).getTimestamp();
        List<TrajectoryPoint4D> detour4D = build4DFromWaypoints(
                detourWaypoints, airspeed, lastTime);
        for (int i = 1; i < detour4D.size(); i++) {
            TrajectoryPoint4D pt = detour4D.get(i);
            pt.setSequenceNumber((long) (result.size()));
            pt.setFlightId(blendStart.getFlightId());
            result.add(pt);
        }

        log.info("Blended trajectory: {} points total ({} original + {} blended + {} detour)",
                result.size(), currentIndex + 1, blendSteps,
                Math.max(0, detour4D.size() - 1));
        return result;
    }

    public List<TrajectoryPoint4D> build4DFromWaypoints(
            List<Waypoint> waypoints, double airspeed, Instant startTime) {

        List<TrajectoryPoint4D> result = new ArrayList<>();
        Instant currentTime = startTime != null ? startTime : Instant.now();
        double stepSeconds = 5.0;

        for (int i = 0; i < waypoints.size() - 1; i++) {
            Waypoint from = waypoints.get(i);
            Waypoint to = waypoints.get(i + 1);
            double dist = from.distanceTo(to);
            double segmentTime = dist / Math.max(1, airspeed);
            int steps = (int) Math.max(1, Math.ceil(segmentTime / stepSeconds));

            double alt1 = safeAlt(from);
            double alt2 = safeAlt(to);
            double bearing = from.bearingTo(to);

            for (int s = 0; s < steps; s++) {
                double frac = s / (double) steps;
                Waypoint interp = Waypoint.interpolate(from, to, frac);

                TrajectoryPoint4D pt = new TrajectoryPoint4D();
                pt.setSequenceNumber((long) result.size());
                pt.setLatitude(interp.getLatitude());
                pt.setLongitude(interp.getLongitude());
                pt.setAltitude(alt1 + (alt2 - alt1) * frac);
                pt.setTimestamp(currentTime);
                pt.setGroundSpeed(airspeed);
                pt.setTrueAirspeed(airspeed);
                pt.setHeading(bearing);
                pt.setFlightPhase(determinePhase(alt1, alt2, frac));
                result.add(pt);

                currentTime = currentTime.plusSeconds((long) stepSeconds);
            }
        }

        if (!waypoints.isEmpty()) {
            Waypoint last = waypoints.get(waypoints.size() - 1);
            TrajectoryPoint4D finalPt = new TrajectoryPoint4D();
            finalPt.setSequenceNumber((long) result.size());
            finalPt.setLatitude(last.getLatitude());
            finalPt.setLongitude(last.getLongitude());
            finalPt.setAltitude(safeAlt(last));
            finalPt.setTimestamp(currentTime);
            finalPt.setGroundSpeed(airspeed);
            finalPt.setFlightPhase("CRUISE");
            result.add(finalPt);
        }

        return result;
    }

    private String determinePhase(double altFrom, double altTo, double frac) {
        double currentAlt = altFrom + (altTo - altFrom) * frac;
        double delta = altTo - altFrom;
        if (Math.abs(delta) < 150) return "CRUISE";
        if (delta > 0) return "CLIMB";
        return "DESCENT";
    }

    private double safeAlt(Waypoint w) {
        return w.getAltitude() != null ? w.getAltitude() : 10000.0;
    }
}
