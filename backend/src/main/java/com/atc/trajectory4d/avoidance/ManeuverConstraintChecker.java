package com.atc.trajectory4d.avoidance;

import com.atc.trajectory4d.bada.BadaService;
import com.atc.trajectory4d.bada.model.AircraftPerformance;
import com.atc.trajectory4d.model.Waypoint;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Component;

import java.util.ArrayList;
import java.util.List;

@Slf4j
@Component
public class ManeuverConstraintChecker {

    private final BadaService badaService;

    public ManeuverConstraintChecker(BadaService badaService) {
        this.badaService = badaService;
    }

    @Data
    @Builder
    @NoArgsConstructor
    @AllArgsConstructor
    public static class ConstraintResult {
        private boolean feasible;
        private List<Violation> violations;
        private double maxBankAngleDeg;
        private double maxClimbGradient;
        private double maxDescentGradient;

        @Data
        @Builder
        @NoArgsConstructor
        @AllArgsConstructor
        public static class Violation {
            private int waypointIndex;
            private String type;
            private double actual;
            private double limit;
            private String description;
        }
    }

    public ConstraintResult checkConstraints(String aircraftType, List<Waypoint> waypoints,
                                              double indicatedAirspeed) {
        ConstraintResult result = new ConstraintResult();
        result.setFeasible(true);
        result.setViolations(new ArrayList<>());

        AircraftPerformance perf = badaService.getPerformance(aircraftType);

        double maxBankAngleDeg = 30.0;
        double vmoto = perf.getPerformance().getOrDefault("V_MO_T", 470.0);
        double trueAirspeed = Math.min(indicatedAirspeed, vmoto);

        double turnRadiusMeters = (trueAirspeed * trueAirspeed)
                / (9.81 * Math.tan(Math.toRadians(maxBankAngleDeg)));
        double minTurnDistance = turnRadiusMeters * Math.PI / 3.0;

        double maxClimbGradient = 0.10;
        double maxDescentGradient = 0.15;
        try {
            Double climbRate = perf.getPerformance().get("ROC_CRZ_MAX");
            if (climbRate != null && trueAirspeed > 0) {
                maxClimbGradient = Math.min(0.15, climbRate / trueAirspeed);
            }
            Double descentRate = perf.getPerformance().get("ROD_APP");
            if (descentRate != null && trueAirspeed > 0) {
                maxDescentGradient = Math.min(0.20, descentRate / trueAirspeed);
            }
        } catch (Exception e) {
            log.warn("Could not compute exact climb/descent limits, using defaults");
        }

        result.setMaxBankAngleDeg(maxBankAngleDeg);
        result.setMaxClimbGradient(maxClimbGradient);
        result.setMaxDescentGradient(maxDescentGradient);

        for (int i = 1; i < waypoints.size() - 1; i++) {
            Waypoint prev = waypoints.get(i - 1);
            Waypoint curr = waypoints.get(i);
            Waypoint next = waypoints.get(i + 1);

            double bearing1 = prev.bearingTo(curr);
            double bearing2 = curr.bearingTo(next);
            double turnAngle = Math.abs(normalizeAngle(bearing2 - bearing1));

            double distPrev = prev.distanceTo(curr);
            double distNext = curr.distanceTo(next);
            double avgDist = (distPrev + distNext) / 2.0;

            if (turnAngle > 5.0 && avgDist < minTurnDistance) {
                ConstraintResult.Violation v = ConstraintResult.Violation.builder()
                        .waypointIndex(i)
                        .type("TURN_RADIUS")
                        .actual(avgDist)
                        .limit(minTurnDistance)
                        .description(String.format(
                                "Turn of %.1f° at waypoint '%s' needs %.0fm arc length, available %.0fm",
                                turnAngle, curr.getName(), minTurnDistance, avgDist))
                        .build();
                result.getViolations().add(v);
                result.setFeasible(false);
            }

            double alt1 = safeAlt(prev);
            double alt2 = safeAlt(curr);
            double alt3 = safeAlt(next);
            double climbGrad1 = distPrev > 0 ? (alt2 - alt1) / distPrev : 0;
            double climbGrad2 = distNext > 0 ? (alt3 - alt2) / distNext : 0;

            if (climbGrad1 > maxClimbGradient) {
                ConstraintResult.Violation v = ConstraintResult.Violation.builder()
                        .waypointIndex(i)
                        .type("CLIMB_GRADIENT")
                        .actual(climbGrad1)
                        .limit(maxClimbGradient)
                        .description(String.format(
                                "Climb gradient %.3f exceeds max %.3f", climbGrad1, maxClimbGradient))
                        .build();
                result.getViolations().add(v);
                result.setFeasible(false);
            }

            if (-climbGrad2 > maxDescentGradient) {
                ConstraintResult.Violation v = ConstraintResult.Violation.builder()
                        .waypointIndex(i)
                        .type("DESCENT_GRADIENT")
                        .actual(-climbGrad2)
                        .limit(maxDescentGradient)
                        .description(String.format(
                                "Descent gradient %.3f exceeds max %.3f", -climbGrad2, maxDescentGradient))
                        .build();
                result.getViolations().add(v);
                result.setFeasible(false);
            }

            double vertRate1 = climbGrad1 * trueAirspeed;
            double vertRate2 = climbGrad2 * trueAirspeed;
            double vertAccel = (vertRate2 - vertRate1) / (Math.max(1, distPrev / trueAirspeed));
            double maxVertAccel = 2.5;

            if (Math.abs(vertAccel) > maxVertAccel) {
                ConstraintResult.Violation v = ConstraintResult.Violation.builder()
                        .waypointIndex(i)
                        .type("VERTICAL_ACCEL")
                        .actual(vertAccel)
                        .limit(maxVertAccel)
                        .description(String.format(
                                "Vertical acceleration %.2f m/s² exceeds limit %.2f", vertAccel, maxVertAccel))
                        .build();
                result.getViolations().add(v);
                result.setFeasible(false);
            }
        }

        if (!result.isFeasible()) {
            log.warn("Maneuver constraints violated: {} issues", result.getViolations().size());
        }

        return result;
    }

    public List<Waypoint> adjustWaypointsForConstraints(String aircraftType,
                                                        List<Waypoint> waypoints,
                                                        double airspeed) {
        ConstraintResult check = checkConstraints(aircraftType, waypoints, airspeed);
        if (check.isFeasible()) return waypoints;

        List<Waypoint> adjusted = new ArrayList<>(waypoints);

        for (ConstraintResult.Violation v : check.getViolations()) {
            int idx = v.getWaypointIndex();
            if (idx < 1 || idx >= adjusted.size() - 1) continue;

            if ("TURN_RADIUS".equals(v.getType())) {
                insertIntermediateWaypoints(adjusted, idx - 1, idx, idx + 1);
            }

            if ("CLIMB_GRADIENT".equals(v.getType()) || "DESCENT_GRADIENT".equals(v.getType())) {
                flattenAltitudeChange(adjusted, idx - 1, idx, idx + 1, check.getMaxClimbGradient());
            }
        }

        check = checkConstraints(aircraftType, adjusted, airspeed);
        if (check.isFeasible()) {
            return adjusted;
        }

        log.warn("Could not fully satisfy constraints after adjustment; remaining violations: {}",
                check.getViolations().size());
        return adjusted;
    }

    private void insertIntermediateWaypoints(List<Waypoint> list, int a, int b, int c) {
        Waypoint prev = list.get(a);
        Waypoint curr = list.get(b);
        Waypoint next = list.get(c);

        Waypoint mid1 = Waypoint.interpolate(prev, curr, 0.5);
        mid1.setName(curr.getName() + "_T1");
        mid1.setAltitude(safeAlt(curr));

        Waypoint mid2 = Waypoint.interpolate(curr, next, 0.5);
        mid2.setName(curr.getName() + "_T2");
        mid2.setAltitude(safeAlt(curr));

        list.remove(b);
        list.add(b, mid2);
        list.add(b, curr);
        list.add(b, mid1);
    }

    private void flattenAltitudeChange(List<Waypoint> list, int a, int b, int c, double maxGrad) {
        Waypoint prev = list.get(a);
        Waypoint curr = list.get(b);
        Waypoint next = list.get(c);

        double dist1 = prev.distanceTo(curr);
        double dist2 = curr.distanceTo(next);
        double maxAltChange1 = dist1 * maxGrad;
        double maxAltChange2 = dist2 * maxGrad;

        double prevAlt = safeAlt(prev);
        double desiredAlt = safeAlt(curr);
        double nextAlt = safeAlt(next);

        double adjustedAlt = prevAlt
                + Math.signum(desiredAlt - prevAlt) * Math.min(Math.abs(desiredAlt - prevAlt), maxAltChange1);

        if (Math.abs(nextAlt - adjustedAlt) > dist2 * maxGrad) {
            adjustedAlt = nextAlt - Math.signum(nextAlt - adjustedAlt) * dist2 * maxGrad;
        }

        curr.setAltitude(adjustedAlt);
    }

    private double safeAlt(Waypoint w) {
        return w.getAltitude() != null ? w.getAltitude() : 10000.0;
    }

    private double normalizeAngle(double angle) {
        while (angle > 180) angle -= 360;
        while (angle < -180) angle += 360;
        return angle;
    }
}
