package com.atc.trajectory4d.avoidance;

import com.atc.trajectory4d.model.RestrictedAirspace;
import com.atc.trajectory4d.model.Waypoint;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;
import lombok.extern.slf4j.Slf4j;

import java.util.*;

@Slf4j
public class TangentGraphBuilder {

    @Data
    @Builder
    @NoArgsConstructor
    @AllArgsConstructor
    public static class GraphNode {
        private Waypoint position;
        private boolean isTangentPoint;
        private boolean isVertex;
        private String airspaceId;
    }

    @Data
    @Builder
    @NoArgsConstructor
    @AllArgsConstructor
    public static class GraphEdge {
        private GraphNode from;
        private GraphNode to;
        private double distance;
        private boolean feasible;
    }

    public static class TangentGraph {
        public final List<GraphNode> nodes = new ArrayList<>();
        public final Map<GraphNode, List<GraphEdge>> adjacency = new HashMap<>();
        public final List<RestrictedAirspace> obstacles = new ArrayList<>();
    }

    private static final double SAFETY_MARGIN_NM = 5.0;
    private static final double SAFETY_MARGIN_METERS = SAFETY_MARGIN_NM * 1852.0;

    public TangentGraph buildGraph(
            Waypoint start,
            Waypoint goal,
            List<RestrictedAirspace> obstacles,
            double cruiseAltitude
    ) {
        TangentGraph graph = new TangentGraph();
        graph.obstacles.addAll(obstacles);

        GraphNode startNode = GraphNode.builder()
                .position(start)
                .isTangentPoint(false)
                .isVertex(false)
                .build();

        GraphNode goalNode = GraphNode.builder()
                .position(goal)
                .isTangentPoint(false)
                .isVertex(false)
                .build();

        graph.nodes.add(startNode);
        graph.nodes.add(goalNode);
        graph.adjacency.put(startNode, new ArrayList<>());
        graph.adjacency.put(goalNode, new ArrayList<>());

        for (RestrictedAirspace airspace : obstacles) {
            if (airspace.getPolygonVertices() == null || airspace.getPolygonVertices().size() < 3) continue;

            for (Waypoint vertex : airspace.getPolygonVertices()) {
                Waypoint offsetVertex = offsetFromObstacleCenter(vertex, airspace, SAFETY_MARGIN_METERS);
                offsetVertex.setAltitude(cruiseAltitude);

                GraphNode node = GraphNode.builder()
                        .position(offsetVertex)
                        .isTangentPoint(false)
                        .isVertex(true)
                        .airspaceId(airspace.getId())
                        .build();

                graph.nodes.add(node);
                graph.adjacency.put(node, new ArrayList<>());
            }

            List<Waypoint> tangentPoints = computeTangentPoints(airspace, start, goal, cruiseAltitude);
            for (Waypoint tp : tangentPoints) {
                GraphNode node = GraphNode.builder()
                        .position(tp)
                        .isTangentPoint(true)
                        .isVertex(false)
                        .airspaceId(airspace.getId())
                        .build();
                graph.nodes.add(node);
                graph.adjacency.put(node, new ArrayList<>());
            }
        }

        addAltitudeLayerNodes(graph, start, goal, obstacles, cruiseAltitude);

        for (int i = 0; i < graph.nodes.size(); i++) {
            GraphNode n1 = graph.nodes.get(i);
            for (int j = i + 1; j < graph.nodes.size(); j++) {
                GraphNode n2 = graph.nodes.get(j);
                if (n1 == n2) continue;

                boolean feasible = isEdgeFeasible(n1.getPosition(), n2.getPosition(), obstacles);
                if (feasible) {
                    double dist = n1.getPosition().distanceTo(n2.getPosition());
                    graph.adjacency.get(n1).add(GraphEdge.builder()
                            .from(n1).to(n2).distance(dist).feasible(true).build());
                    graph.adjacency.get(n2).add(GraphEdge.builder()
                            .from(n2).to(n1).distance(dist).feasible(true).build());
                }
            }
        }

        log.info("Tangent graph built: {} nodes, obstacles: {}", graph.nodes.size(), obstacles.size());
        return graph;
    }

    private Waypoint offsetFromObstacleCenter(Waypoint vertex, RestrictedAirspace airspace, double margin) {
        Waypoint center = computeCentroid(airspace.getPolygonVertices());
        double bearing = center.bearingTo(vertex);
        double dist = center.distanceTo(vertex) + margin;

        double centerLatRad = Math.toRadians(center.getLatitude());
        double bearingRad = Math.toRadians(bearing);
        double R = 6371000.0;
        double angularDist = dist / R;

        double lat2 = Math.asin(Math.sin(centerLatRad) * Math.cos(angularDist)
                + Math.cos(centerLatRad) * Math.sin(angularDist) * Math.cos(bearingRad));

        double lon2 = Math.toRadians(center.getLongitude()) + Math.atan2(
                Math.sin(bearingRad) * Math.sin(angularDist) * Math.cos(centerLatRad),
                Math.cos(angularDist) - Math.sin(centerLatRad) * Math.sin(lat2)
        );

        return Waypoint.builder()
                .name(vertex.getName() + "_offset")
                .latitude(Math.toDegrees(lat2))
                .longitude(Math.toDegrees(lon2))
                .altitude(vertex.getAltitude())
                .build();
    }

    private Waypoint computeCentroid(List<Waypoint> polygon) {
        if (polygon == null || polygon.isEmpty()) return Waypoint.builder().latitude(0).longitude(0).build();
        double latSum = 0, lonSum = 0;
        for (Waypoint w : polygon) {
            latSum += w.getLatitude();
            lonSum += w.getLongitude();
        }
        int n = polygon.size();
        return Waypoint.builder()
                .name("centroid")
                .latitude(latSum / n)
                .longitude(lonSum / n)
                .build();
    }

    private List<Waypoint> computeTangentPoints(RestrictedAirspace airspace,
                                                  Waypoint external1, Waypoint external2,
                                                  double altitude) {
        List<Waypoint> result = new ArrayList<>();
        if (airspace.getPolygonVertices() == null) return result;

        Waypoint center = computeCentroid(airspace.getPolygonVertices());
        center.setAltitude(altitude);

        for (Waypoint external : Arrays.asList(external1, external2)) {
            for (Waypoint vertex : airspace.getPolygonVertices()) {
                Waypoint candidate = offsetFromObstacleCenter(vertex, airspace, SAFETY_MARGIN_METERS);
                candidate.setAltitude(altitude);

                if (!segmentIntersectsPolygon(external, candidate, airspace.getPolygonVertices())) {
                    result.add(candidate);
                }
            }
        }

        Set<String> dedup = new HashSet<>();
        List<Waypoint> unique = new ArrayList<>();
        for (Waypoint w : result) {
            String key = String.format("%.4f_%.4f", w.getLatitude(), w.getLongitude());
            if (dedup.add(key)) unique.add(w);
        }
        return unique;
    }

    private void addAltitudeLayerNodes(TangentGraph graph, Waypoint start, Waypoint goal,
                                        List<RestrictedAirspace> obstacles, double cruiseAltitude) {
        double[] altLayers = {cruiseAltitude - 1500, cruiseAltitude, cruiseAltitude + 1500};

        for (RestrictedAirspace airspace : obstacles) {
            Waypoint center = computeCentroid(airspace.getPolygonVertices());
            for (double alt : altLayers) {
                if (alt < 1000) continue;
                Waypoint wp = Waypoint.builder()
                        .name("alt_layer_" + (int) alt)
                        .latitude(center.getLatitude() + 0.15)
                        .longitude(center.getLongitude() + 0.15)
                        .altitude(alt)
                        .build();
                GraphNode node = GraphNode.builder()
                        .position(wp)
                        .isTangentPoint(true)
                        .isVertex(false)
                        .airspaceId(airspace.getId())
                        .build();
                graph.nodes.add(node);
                graph.adjacency.put(node, new ArrayList<>());
            }
        }
    }

    public static boolean isEdgeFeasible(Waypoint from, Waypoint to, List<RestrictedAirspace> obstacles) {
        if (obstacles == null) return true;

        int samples = 20;
        for (int i = 0; i <= samples; i++) {
            double frac = i / (double) samples;
            Waypoint sampled = Waypoint.interpolate(from, to, frac);
            double sampleAlt = interpolateAltitude(from, to, frac);

            for (RestrictedAirspace airspace : obstacles) {
                if (airspace.containsPoint(sampled.getLatitude(), sampled.getLongitude(),
                        sampleAlt, java.time.Instant.now())) {
                    return false;
                }
            }
        }
        return true;
    }

    private static double interpolateAltitude(Waypoint from, Waypoint to, double frac) {
        double a1 = from.getAltitude() != null ? from.getAltitude() : 10000;
        double a2 = to.getAltitude() != null ? to.getAltitude() : 10000;
        return a1 + (a2 - a1) * frac;
    }

    private boolean segmentIntersectsPolygon(Waypoint a, Waypoint b, List<Waypoint> polygon) {
        if (polygon == null || polygon.size() < 3) return false;
        int samples = 10;
        for (int i = 0; i <= samples; i++) {
            double frac = i / (double) samples;
            Waypoint p = Waypoint.interpolate(a, b, frac);
            if (RestrictedAirspace.pointInPolygon(p.getLatitude(), p.getLongitude(), polygon)) {
                return true;
            }
        }
        return false;
    }
}
