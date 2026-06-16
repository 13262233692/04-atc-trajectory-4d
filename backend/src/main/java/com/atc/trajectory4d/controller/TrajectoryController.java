package com.atc.trajectory4d.controller;

import com.atc.trajectory4d.model.TrajectoryPoint4D;
import com.atc.trajectory4d.model.dto.ApiResponse;
import com.atc.trajectory4d.model.dto.FlightPlanRequest;
import com.atc.trajectory4d.model.FlightPlan;
import com.atc.trajectory4d.service.TrajectoryService;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import java.util.HashMap;
import java.util.List;
import java.util.Map;

@Slf4j
@RestController
@RequestMapping("/trajectory")
@RequiredArgsConstructor
public class TrajectoryController {

    private final TrajectoryService trajectoryService;

    @PostMapping("/calculate")
    public ResponseEntity<ApiResponse<Map<String, Object>>> calculateTrajectory(
            @Valid @RequestBody FlightPlanRequest request) {

        log.info("Received trajectory calculation request for flight: {}", request.getFlightId());

        try {
            FlightPlan flightPlan = convertToFlightPlan(request);
            List<TrajectoryPoint4D> trajectory = trajectoryService.calculateTrajectory(flightPlan);

            Map<String, Object> result = new HashMap<>();
            result.put("flightId", request.getFlightId());
            result.put("pointCount", trajectory.size());
            result.put("trajectory", trajectory);

            if (!trajectory.isEmpty()) {
                result.put("departurePoint", trajectory.get(0));
                result.put("arrivalPoint", trajectory.get(trajectory.size() - 1));
                double totalTime = (trajectory.size() - 1) * 5.0;
                result.put("totalFlightTimeSeconds", totalTime);
                result.put("totalFlightTimeMinutes", totalTime / 60.0);
                result.put("totalDistanceKm", trajectory.get(0).getDistanceToDestination() / 1000.0);
            }

            return ResponseEntity.ok(ApiResponse.success(
                    String.format("Trajectory calculated with %d points", trajectory.size()),
                    result
            ));

        } catch (Exception e) {
            log.error("Failed to calculate trajectory for flight {}", request.getFlightId(), e);
            return ResponseEntity.internalServerError()
                    .body(ApiResponse.error("Failed to calculate trajectory: " + e.getMessage()));
        }
    }

    @GetMapping("/{flightId}")
    public ResponseEntity<ApiResponse<List<TrajectoryPoint4D>>> getTrajectory(
            @PathVariable String flightId) {

        List<TrajectoryPoint4D> trajectory = trajectoryService.getTrajectory(flightId);
        if (trajectory == null) {
            return ResponseEntity.notFound().build();
        }

        return ResponseEntity.ok(ApiResponse.success(
                String.format("Found %d trajectory points", trajectory.size()),
                trajectory
        ));
    }

    @GetMapping("/{flightId}/current")
    public ResponseEntity<ApiResponse<TrajectoryPoint4D>> getCurrentPoint(
            @PathVariable String flightId) {

        TrajectoryPoint4D point = trajectoryService.getCurrentPoint(flightId);
        if (point == null) {
            return ResponseEntity.notFound().build();
        }

        return ResponseEntity.ok(ApiResponse.success(point));
    }

    @GetMapping("/{flightId}/point/{sequence}")
    public ResponseEntity<ApiResponse<TrajectoryPoint4D>> getTrajectoryPoint(
            @PathVariable String flightId,
            @PathVariable int sequence) {

        List<TrajectoryPoint4D> trajectory = trajectoryService.getTrajectory(flightId);
        if (trajectory == null) {
            return ResponseEntity.notFound().build();
        }

        if (sequence < 0 || sequence >= trajectory.size()) {
            return ResponseEntity.badRequest()
                    .body(ApiResponse.badRequest("Invalid sequence number"));
        }

        return ResponseEntity.ok(ApiResponse.success(trajectory.get(sequence)));
    }

    @PostMapping("/stream/{flightId}/start")
    public ResponseEntity<ApiResponse<Map<String, Object>>> startStreaming(
            @PathVariable String flightId,
            @RequestParam(defaultValue = "false") boolean realtime) {

        List<TrajectoryPoint4D> trajectory = trajectoryService.getTrajectory(flightId);
        if (trajectory == null) {
            return ResponseEntity.notFound().build();
        }

        if (realtime) {
            trajectoryService.startRealtimeStreaming(flightId);
        } else {
            trajectoryService.startStreaming(flightId);
        }

        Map<String, Object> result = new HashMap<>();
        result.put("flightId", flightId);
        result.put("streaming", true);
        result.put("realtime", realtime);
        result.put("totalPoints", trajectory.size());

        return ResponseEntity.ok(ApiResponse.success(
                String.format("Started %s streaming for flight %s",
                        realtime ? "realtime" : "fast", flightId),
                result
        ));
    }

    @PostMapping("/stream/{flightId}/stop")
    public ResponseEntity<ApiResponse<Map<String, Object>>> stopStreaming(
            @PathVariable String flightId) {

        trajectoryService.stopStreaming(flightId);

        Map<String, Object> result = new HashMap<>();
        result.put("flightId", flightId);
        result.put("streaming", false);
        result.put("currentPosition", trajectoryService.getStreamPosition(flightId));

        return ResponseEntity.ok(ApiResponse.success("Streaming stopped", result));
    }

    @DeleteMapping("/{flightId}")
    public ResponseEntity<ApiResponse<Void>> deleteTrajectory(
            @PathVariable String flightId) {

        boolean deleted = trajectoryService.deleteTrajectory(flightId);
        if (!deleted) {
            return ResponseEntity.notFound().build();
        }

        return ResponseEntity.ok(ApiResponse.success("Trajectory deleted", null));
    }

    @GetMapping("/active")
    public ResponseEntity<ApiResponse<List<String>>> getActiveFlights() {
        List<String> activeFlights = trajectoryService.getActiveFlights();
        return ResponseEntity.ok(ApiResponse.success(activeFlights));
    }

    @GetMapping("/{flightId}/status")
    public ResponseEntity<ApiResponse<Map<String, Object>>> getFlightStatus(
            @PathVariable String flightId) {

        List<TrajectoryPoint4D> trajectory = trajectoryService.getTrajectory(flightId);
        if (trajectory == null) {
            return ResponseEntity.notFound().build();
        }

        FlightPlan flightPlan = trajectoryService.getFlightPlan(flightId);
        TrajectoryPoint4D currentPoint = trajectoryService.getCurrentPoint(flightId);
        int streamPosition = trajectoryService.getStreamPosition(flightId);
        int totalPoints = trajectory.size();
        double progress = totalPoints > 0 ? (double) streamPosition / totalPoints * 100.0 : 0.0;

        Map<String, Object> status = new HashMap<>();
        status.put("flightId", flightId);
        status.put("flightPlan", flightPlan);
        status.put("currentPosition", currentPoint);
        status.put("totalPoints", totalPoints);
        status.put("streamPosition", streamPosition);
        status.put("progressPercent", progress);
        status.put("isStreaming", streamPosition > 0 && streamPosition < totalPoints);

        return ResponseEntity.ok(ApiResponse.success(status));
    }

    private FlightPlan convertToFlightPlan(FlightPlanRequest request) {
        return FlightPlan.builder()
                .flightId(request.getFlightId())
                .aircraftType(request.getAircraftType())
                .airline(request.getAirline())
                .flightNumber(request.getFlightNumber())
                .departureAirport(request.getDepartureAirport())
                .arrivalAirport(request.getArrivalAirport())
                .departureTime(request.getDepartureTime())
                .initialMass(request.getInitialMass() > 0 ? request.getInitialMass() : 70000.0)
                .fuelMass(request.getFuelMass() > 0 ? request.getFuelMass() : 15000.0)
                .payloadMass(request.getPayloadMass() > 0 ? request.getPayloadMass() : 10000.0)
                .cruiseAltitude(request.getCruiseAltitude() > 0 ? request.getCruiseAltitude() : 11000.0)
                .cruiseSpeed(request.getCruiseSpeed() > 0 ? request.getCruiseSpeed() : 230.0)
                .cruiseMach(request.getCruiseMach() > 0 ? request.getCruiseMach() : 0.78)
                .waypoints(request.getWaypoints())
                .build();
    }
}
