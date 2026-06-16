package com.atc.trajectory4d.controller;

import com.atc.trajectory4d.avoidance.AirspaceAvoidanceService;
import com.atc.trajectory4d.avoidance.AirspaceAvoidanceService.AvoidanceResult;
import com.atc.trajectory4d.model.RestrictedAirspace;
import com.atc.trajectory4d.model.dto.ApiResponse;
import com.atc.trajectory4d.model.dto.RestrictedAirspaceRequest;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import java.util.List;

@Slf4j
@RestController
@RequestMapping("/api/airspace")
@RequiredArgsConstructor
public class AirspaceController {

    private final AirspaceAvoidanceService avoidanceService;

    @GetMapping
    public ResponseEntity<ApiResponse<List<RestrictedAirspace>>> getAllAirspaces() {
        try {
            List<RestrictedAirspace> airspaces = avoidanceService.getAllAirspaces();
            return ResponseEntity.ok(ApiResponse.success(
                    String.format("Found %d restricted airspaces", airspaces.size()),
                    airspaces));
        } catch (Exception e) {
            log.error("Failed to fetch airspaces", e);
            return ResponseEntity.internalServerError()
                    .body(ApiResponse.error("Failed to fetch airspaces: " + e.getMessage()));
        }
    }

    @GetMapping("/{airspaceId}")
    public ResponseEntity<ApiResponse<RestrictedAirspace>> getAirspace(@PathVariable String airspaceId) {
        return avoidanceService.getAirspace(airspaceId)
                .map(a -> ResponseEntity.ok(ApiResponse.success("Airspace found", a)))
                .orElse(ResponseEntity.status(404)
                        .body(ApiResponse.notFound("Airspace not found: " + airspaceId)));
    }

    @PostMapping
    public ResponseEntity<ApiResponse<RestrictedAirspace>> createAirspace(
            @RequestBody RestrictedAirspaceRequest request) {
        try {
            if (request.getPolygonVertices() == null || request.getPolygonVertices().size() < 3) {
                return ResponseEntity.badRequest()
                        .body(ApiResponse.badRequest("Polygon must have at least 3 vertices"));
            }
            RestrictedAirspace airspace = avoidanceService.createAirspace(request);
            log.info("Created restricted airspace: {}", airspace.getId());
            return ResponseEntity.ok(ApiResponse.success("Restricted airspace created", airspace));
        } catch (Exception e) {
            log.error("Failed to create airspace", e);
            return ResponseEntity.internalServerError()
                    .body(ApiResponse.error("Failed to create airspace: " + e.getMessage()));
        }
    }

    @PutMapping("/{airspaceId}")
    public ResponseEntity<ApiResponse<RestrictedAirspace>> updateAirspace(
            @PathVariable String airspaceId,
            @RequestBody RestrictedAirspaceRequest request) {
        try {
            return avoidanceService.updateAirspace(airspaceId, request)
                    .map(a -> ResponseEntity.ok(ApiResponse.success("Airspace updated", a)))
                    .orElse(ResponseEntity.status(404)
                            .body(ApiResponse.notFound("Airspace not found: " + airspaceId)));
        } catch (Exception e) {
            log.error("Failed to update airspace {}", airspaceId, e);
            return ResponseEntity.internalServerError()
                    .body(ApiResponse.error("Failed to update airspace: " + e.getMessage()));
        }
    }

    @DeleteMapping("/{airspaceId}")
    public ResponseEntity<ApiResponse<Void>> deleteAirspace(@PathVariable String airspaceId) {
        try {
            boolean deleted = avoidanceService.deleteAirspace(airspaceId);
            if (deleted) {
                return ResponseEntity.ok(ApiResponse.success("Airspace deleted: " + airspaceId, null));
            }
            return ResponseEntity.status(404)
                    .body(ApiResponse.notFound("Airspace not found: " + airspaceId));
        } catch (Exception e) {
            log.error("Failed to delete airspace {}", airspaceId, e);
            return ResponseEntity.internalServerError()
                    .body(ApiResponse.error("Failed to delete airspace: " + e.getMessage()));
        }
    }

    @PostMapping("/reroute/{flightId}")
    public ResponseEntity<ApiResponse<AvoidanceResult>> rerouteFlight(@PathVariable String flightId) {
        try {
            AvoidanceResult result = avoidanceService.rerouteFlight(flightId);
            if (result.isSuccess()) {
                return ResponseEntity.ok(ApiResponse.success(result.getMessage(), result));
            }
            return ResponseEntity.status(409)
                    .body(ApiResponse.error(result.getMessage(), 409));
        } catch (Exception e) {
            log.error("Failed to reroute flight {}", flightId, e);
            return ResponseEntity.internalServerError()
                    .body(ApiResponse.error("Failed to reroute flight: " + e.getMessage()));
        }
    }

    @PostMapping("/trigger/{airspaceId}")
    public ResponseEntity<ApiResponse<Void>> triggerAvoidance(@PathVariable String airspaceId) {
        try {
            return avoidanceService.getAirspace(airspaceId)
                    .map(a -> {
                        avoidanceService.triggerAvoidanceForAffectedFlights(a);
                        return ResponseEntity.ok(ApiResponse.<Void>success(
                                "Reroute triggered for airspace " + airspaceId, null));
                    })
                    .orElse(ResponseEntity.status(404)
                            .body(ApiResponse.notFound("Airspace not found: " + airspaceId)));
        } catch (Exception e) {
            log.error("Failed to trigger avoidance for airspace {}", airspaceId, e);
            return ResponseEntity.internalServerError()
                    .body(ApiResponse.error("Failed to trigger avoidance: " + e.getMessage()));
        }
    }
}
