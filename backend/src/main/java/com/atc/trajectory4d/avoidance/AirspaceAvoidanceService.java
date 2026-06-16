package com.atc.trajectory4d.avoidance;

import com.atc.trajectory4d.avoidance.AStarPathfinder.PathResult;
import com.atc.trajectory4d.avoidance.ManeuverConstraintChecker.ConstraintResult;
import com.atc.trajectory4d.avoidance.TangentGraphBuilder.TangentGraph;
import com.atc.trajectory4d.model.FlightPlan;
import com.atc.trajectory4d.model.RestrictedAirspace;
import com.atc.trajectory4d.model.TrajectoryPoint4D;
import com.atc.trajectory4d.model.Waypoint;
import com.atc.trajectory4d.model.dto.RestrictedAirspaceRequest;
import com.atc.trajectory4d.model.entity.RestrictedAirspaceEntity;
import com.atc.trajectory4d.repository.RestrictedAirspaceRepository;
import com.atc.trajectory4d.service.TrajectoryService;
import com.atc.trajectory4d.websocket.WebSocketService;
import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.ObjectMapper;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.scheduling.annotation.Async;
import org.springframework.stereotype.Service;

import java.time.Instant;
import java.util.*;
import java.util.concurrent.ConcurrentHashMap;

@Slf4j
@Service
@RequiredArgsConstructor
public class AirspaceAvoidanceService {

    private final RestrictedAirspaceRepository airspaceRepository;
    private final ManeuverConstraintChecker constraintChecker;
    private final TrajectorySmoother trajectorySmoother;
    private final TrajectoryService trajectoryService;
    private final WebSocketService webSocketService;
    private final ObjectMapper objectMapper;

    private final Map<String, RestrictedAirspace> activeAirspaces = new ConcurrentHashMap<>();

    @Data
    @Builder
    @NoArgsConstructor
    @AllArgsConstructor
    public static class AvoidanceResult {
        private String flightId;
        private List<Waypoint> originalRoute;
        private List<Waypoint> detourRoute;
        private List<TrajectoryPoint4D> blendedTrajectory;
        private boolean success;
        private double extraDistanceMeters;
        private double extraTimeSeconds;
        private String message;
        private ConstraintResult constraintCheck;
    }

    public List<RestrictedAirspace> getAllAirspaces() {
        return new ArrayList<>(activeAirspaces.values());
    }

    public Optional<RestrictedAirspace> getAirspace(String airspaceId) {
        return Optional.ofNullable(activeAirspaces.get(airspaceId));
    }

    public RestrictedAirspace createAirspace(RestrictedAirspaceRequest request) {
        String id = request.getName() != null
                ? "RA-" + request.getName().replaceAll("[^A-Za-z0-9]", "") + "-" + UUID.randomUUID().toString().substring(0, 6)
                : "RA-" + UUID.randomUUID().toString().substring(0, 8);

        RestrictedAirspace airspace = RestrictedAirspace.builder()
                .id(id)
                .name(request.getName() != null ? request.getName() : id)
                .reason(request.getReason())
                .level(request.getLevel() != null ? request.getLevel() : RestrictedAirspace.RestrictionLevel.PROHIBITED)
                .shape(request.getShape() != null ? request.getShape() : RestrictedAirspace.AirspaceShape.POLYGON)
                .polygonVertices(request.toWaypoints())
                .minAltitude(request.getMinAltitude() != null ? request.getMinAltitude() : 0.0)
                .maxAltitude(request.getMaxAltitude() != null ? request.getMaxAltitude() : 15000.0)
                .effectiveFrom(request.getEffectiveFrom() != null ? request.getEffectiveFrom() : Instant.now())
                .effectiveTo(request.getEffectiveTo())
                .active(request.getActive() != null ? request.getActive() : true)
                .createdAt(Instant.now())
                .updatedAt(Instant.now())
                .build();

        saveToRepository(airspace);
        activeAirspaces.put(id, airspace);
        notifyAirspaceCreated(airspace);

        log.info("Created restricted airspace: {} ({} vertices, {}-{} m)",
                id, airspace.getPolygonVertices().size(),
                airspace.getMinAltitude(), airspace.getMaxAltitude());

        triggerAvoidanceForAffectedFlights(airspace);
        return airspace;
    }

    public Optional<RestrictedAirspace> updateAirspace(String airspaceId, RestrictedAirspaceRequest request) {
        RestrictedAirspace existing = activeAirspaces.get(airspaceId);
        if (existing == null) return Optional.empty();

        if (request.getName() != null) existing.setName(request.getName());
        if (request.getReason() != null) existing.setReason(request.getReason());
        if (request.getLevel() != null) existing.setLevel(request.getLevel());
        if (request.getMinAltitude() != null) existing.setMinAltitude(request.getMinAltitude());
        if (request.getMaxAltitude() != null) existing.setMaxAltitude(request.getMaxAltitude());
        if (request.getEffectiveFrom() != null) existing.setEffectiveFrom(request.getEffectiveFrom());
        if (request.getEffectiveTo() != null) existing.setEffectiveTo(request.getEffectiveTo());
        if (request.getActive() != null) existing.setActive(request.getActive());
        if (request.getPolygonVertices() != null) existing.setPolygonVertices(request.toWaypoints());
        existing.setUpdatedAt(Instant.now());

        saveToRepository(existing);
        activeAirspaces.put(airspaceId, existing);
        log.info("Updated restricted airspace: {}", airspaceId);
        triggerAvoidanceForAffectedFlights(existing);

        return Optional.of(existing);
    }

    public boolean deleteAirspace(String airspaceId) {
        activeAirspaces.remove(airspaceId);
        try {
            airspaceRepository.deleteByAirspaceId(airspaceId);
        } catch (Exception e) {
            log.warn("Could not delete airspace from DB: {}", airspaceId, e);
        }
        notifyAirspaceRemoved(airspaceId);
        log.info("Deleted restricted airspace: {}", airspaceId);
        return true;
    }

    @Async
    public void triggerAvoidanceForAffectedFlights(RestrictedAirspace airspace) {
        Map<String, FlightPlan> activePlans = trajectoryService.getActiveFlightPlans();
        log.info("Checking {} active flights for conflict with airspace {}",
                activePlans.size(), airspace.getId());

        for (Map.Entry<String, FlightPlan> entry : activePlans.entrySet()) {
            String flightId = entry.getKey();
            FlightPlan plan = entry.getValue();
            if (isFlightAffected(plan, airspace)) {
                log.info("Flight {} affected by airspace {} - initiating reroute",
                        flightId, airspace.getId());
                rerouteFlight(flightId);
            }
        }
    }

    private boolean isFlightAffected(FlightPlan plan, RestrictedAirspace airspace) {
        if (plan == null || plan.getWaypoints() == null) return false;
        for (int i = 0; i < plan.getWaypoints().size() - 1; i++) {
            Waypoint from = plan.getWaypoints().get(i);
            Waypoint to = plan.getWaypoints().get(i + 1);
            if (segmentIntersectsAirspace(from, to, airspace)) {
                return true;
            }
        }
        return false;
    }

    private boolean segmentIntersectsAirspace(Waypoint from, Waypoint to, RestrictedAirspace airspace) {
        int samples = 20;
        for (int s = 0; s <= samples; s++) {
            double frac = s / (double) samples;
            Waypoint sample = Waypoint.interpolate(from, to, frac);
            double alt = from.getAltitude() != null && to.getAltitude() != null
                    ? from.getAltitude() + (to.getAltitude() - from.getAltitude()) * frac
                    : 10000.0;
            if (airspace.containsPoint(sample.getLatitude(), sample.getLongitude(),
                    alt, Instant.now())) {
                return true;
            }
        }
        return false;
    }

    public AvoidanceResult rerouteFlight(String flightId) {
        FlightPlan plan = trajectoryService.getFlightPlan(flightId);
        if (plan == null) {
            return AvoidanceResult.builder()
                    .flightId(flightId)
                    .success(false)
                    .message("Flight plan not found")
                    .build();
        }

        List<RestrictedAirspace> active = new ArrayList<>(activeAirspaces.values()).stream()
                .filter(a -> a.isActiveAtTime(Instant.now()))
                .toList();

        if (active.isEmpty()) {
            return AvoidanceResult.builder()
                    .flightId(flightId)
                    .success(true)
                    .message("No active restricted airspaces")
                    .build();
        }

        List<Waypoint> originalRoute = plan.getWaypoints();
        if (originalRoute == null || originalRoute.size() < 2) {
            return AvoidanceResult.builder()
                    .flightId(flightId)
                    .success(false)
                    .message("Insufficient waypoints in plan")
                    .build();
        }

        Waypoint start = findCurrentPosition(flightId, originalRoute);
        Waypoint goal = originalRoute.get(originalRoute.size() - 1);
        double cruiseAlt = plan.getCruiseAltitude() != null ? plan.getCruiseAltitude() : 10668.0;

        TangentGraphBuilder graphBuilder = new TangentGraphBuilder();
        TangentGraph graph = graphBuilder.buildGraph(start, goal, active, cruiseAlt);

        AStarPathfinder pathfinder = new AStarPathfinder();
        PathResult pathResult = pathfinder.findPath(graph, start, goal);

        if (!pathResult.isFound()) {
            log.warn("A* failed for flight {}, returning original route", flightId);
            return AvoidanceResult.builder()
                    .flightId(flightId)
                    .originalRoute(originalRoute)
                    .detourRoute(originalRoute)
                    .success(false)
                    .message("No feasible path found, keeping original route")
                    .build();
        }

        List<Waypoint> adjusted = constraintChecker.adjustWaypointsForConstraints(
                plan.getAircraftType(), pathResult.getWaypoints(),
                plan.getCruiseSpeed() != null ? plan.getCruiseSpeed() : 230.0);

        ConstraintResult constraintResult = constraintChecker.checkConstraints(
                plan.getAircraftType(), adjusted,
                plan.getCruiseSpeed() != null ? plan.getCruiseSpeed() : 230.0);

        List<Waypoint> smoothed = trajectorySmoother.smoothPath(adjusted, 3);

        double origDist = computeRouteDistance(originalRoute);
        double newDist = computeRouteDistance(smoothed);
        double extraDist = Math.max(0, newDist - origDist);
        double speed = plan.getCruiseSpeed() != null ? plan.getCruiseSpeed() : 230.0;
        double extraTime = extraDist / speed;

        List<TrajectoryPoint4D> currentTrajectory = trajectoryService.getTrajectoryPoints(flightId);
        int currentIdx = trajectoryService.getCurrentPointIndex(flightId);

        List<TrajectoryPoint4D> blended = trajectorySmoother.blendTrajectories(
                currentTrajectory, smoothed, currentIdx, 30.0, speed);

        AvoidanceResult result = AvoidanceResult.builder()
                .flightId(flightId)
                .originalRoute(originalRoute)
                .detourRoute(smoothed)
                .blendedTrajectory(blended)
                .success(true)
                .extraDistanceMeters(extraDist)
                .extraTimeSeconds(extraTime)
                .message(String.format("Rerouted: +%.0fm, +%.0fs", extraDist, extraTime))
                .constraintCheck(constraintResult)
                .build();

        trajectoryService.updateFlightTrajectory(flightId, blended);
        notifyRerouteComplete(result);
        streamUpdatedTrajectory(flightId, blended, currentIdx);

        log.info("Reroute complete for {}: {} waypoints, +{}m",
                flightId, smoothed.size(), (long) extraDist);

        return result;
    }

    private Waypoint findCurrentPosition(String flightId, List<Waypoint> fallbackRoute) {
        TrajectoryPoint4D current = trajectoryService.getCurrentPoint(flightId);
        if (current != null) {
            return Waypoint.builder()
                    .name("CURRENT")
                    .latitude(current.getLatitude())
                    .longitude(current.getLongitude())
                    .altitude(current.getAltitude())
                    .build();
        }
        return fallbackRoute.get(0);
    }

    private double computeRouteDistance(List<Waypoint> waypoints) {
        double dist = 0;
        for (int i = 0; i < waypoints.size() - 1; i++) {
            dist += waypoints.get(i).distanceTo(waypoints.get(i + 1));
        }
        return dist;
    }

    @Async
    public void streamUpdatedTrajectory(String flightId,
                                         List<TrajectoryPoint4D> trajectory,
                                         int startIndex) {
        int step = Math.max(1, trajectory.size() / 100);
        for (int i = startIndex; i < trajectory.size(); i += step) {
            webSocketService.sendTrajectoryPoint(flightId, trajectory.get(i));
            try {
                Thread.sleep(20);
            } catch (InterruptedException ignored) {
                Thread.currentThread().interrupt();
                return;
            }
        }
    }

    private void saveToRepository(RestrictedAirspace airspace) {
        try {
            RestrictedAirspaceEntity entity = new RestrictedAirspaceEntity();
            entity.setAirspaceId(airspace.getId());
            entity.setName(airspace.getName());
            entity.setReason(airspace.getReason());
            entity.setLevel(airspace.getLevel());
            entity.setShape(airspace.getShape());
            entity.setMinAltitude(airspace.getMinAltitude());
            entity.setMaxAltitude(airspace.getMaxAltitude());
            entity.setEffectiveFrom(airspace.getEffectiveFrom());
            entity.setEffectiveTo(airspace.getEffectiveTo());
            entity.setActive(airspace.isActive());

            Map<String, Object> polyMap = objectMapper.convertValue(
                    airspace.getPolygonVertices(), new TypeReference<>() {});
            entity.setPolygonVertices(polyMap);

            RestrictedAirspaceEntity existing = airspaceRepository.findByAirspaceId(airspace.getId()).orElse(null);
            if (existing != null) {
                entity.setId(existing.getId());
            }
            airspaceRepository.save(entity);
        } catch (Exception e) {
            log.warn("Could not persist airspace {} to DB: {}", airspace.getId(), e.getMessage());
        }
    }

    private void notifyAirspaceCreated(RestrictedAirspace airspace) {
        try {
            Map<String, Object> msg = Map.of(
                    "type", "AIRSPACE_CREATED",
                    "airspace", airspace
            );
            webSocketService.broadcastNotification(objectMapper.writeValueAsString(msg));
        } catch (Exception e) {
            log.warn("Failed to broadcast airspace creation", e);
        }
    }

    private void notifyAirspaceRemoved(String airspaceId) {
        try {
            Map<String, Object> msg = Map.of(
                    "type", "AIRSPACE_REMOVED",
                    "airspaceId", airspaceId
            );
            webSocketService.broadcastNotification(objectMapper.writeValueAsString(msg));
        } catch (Exception e) {
            log.warn("Failed to broadcast airspace removal", e);
        }
    }

    private void notifyRerouteComplete(AvoidanceResult result) {
        try {
            Map<String, Object> msg = Map.of(
                    "type", "REROUTE_COMPLETE",
                    "flightId", result.getFlightId(),
                    "success", result.isSuccess(),
                    "extraDistanceMeters", result.getExtraDistanceMeters(),
                    "extraTimeSeconds", result.getExtraTimeSeconds(),
                    "detourWaypoints", result.getDetourRoute()
            );
            webSocketService.broadcastNotification(objectMapper.writeValueAsString(msg));
        } catch (Exception e) {
            log.warn("Failed to broadcast reroute completion", e);
        }
    }
}
