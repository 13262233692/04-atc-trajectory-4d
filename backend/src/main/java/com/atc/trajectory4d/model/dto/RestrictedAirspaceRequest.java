package com.atc.trajectory4d.model.dto;

import com.atc.trajectory4d.model.RestrictedAirspace;
import com.atc.trajectory4d.model.Waypoint;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.time.Instant;
import java.util.List;

@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class RestrictedAirspaceRequest {

    private String name;
    private String reason;
    private RestrictedAirspace.RestrictionLevel level;
    private RestrictedAirspace.AirspaceShape shape;
    private List<WaypointDto> polygonVertices;
    private Double minAltitude;
    private Double maxAltitude;
    private Instant effectiveFrom;
    private Instant effectiveTo;
    private Boolean active;

    @Data
    @Builder
    @NoArgsConstructor
    @AllArgsConstructor
    public static class WaypointDto {
        private String name;
        private Double latitude;
        private Double longitude;
        private Double altitude;
    }

    public List<Waypoint> toWaypoints() {
        if (polygonVertices == null) return null;
        return polygonVertices.stream()
            .map(dto -> Waypoint.builder()
                .name(dto.getName())
                .latitude(dto.getLatitude())
                .longitude(dto.getLongitude())
                .altitude(dto.getAltitude() != null ? dto.getAltitude() : 0.0)
                .build())
            .toList();
    }
}
