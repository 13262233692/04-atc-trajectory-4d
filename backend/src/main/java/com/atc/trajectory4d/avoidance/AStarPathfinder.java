package com.atc.trajectory4d.avoidance;

import com.atc.trajectory4d.avoidance.TangentGraphBuilder.*;
import com.atc.trajectory4d.model.Waypoint;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;
import lombok.extern.slf4j.Slf4j;

import java.util.*;

@Slf4j
public class AStarPathfinder {

    @Data
    @Builder
    @NoArgsConstructor
    @AllArgsConstructor
    public static class PathResult {
        private List<Waypoint> waypoints;
        private double totalDistance;
        private boolean found;
        private int nodesExpanded;
    }

    private static class AStarNode implements Comparable<AStarNode> {
        GraphNode node;
        double gCost;
        double fCost;
        AStarNode parent;

        AStarNode(GraphNode node, double gCost, double hCost, AStarNode parent) {
            this.node = node;
            this.gCost = gCost;
            this.fCost = gCost + hCost;
            this.parent = parent;
        }

        @Override
        public int compareTo(AStarNode other) {
            return Double.compare(this.fCost, other.fCost);
        }
    }

    public PathResult findPath(TangentGraph graph, Waypoint start, Waypoint goal) {
        GraphNode startNode = findNodeNear(graph, start, 5000);
        GraphNode goalNode = findNodeNear(graph, goal, 5000);

        if (startNode == null) {
            startNode = GraphNode.builder().position(start).build();
            graph.nodes.add(startNode);
            graph.adjacency.put(startNode, new ArrayList<>());
            connectToNearbyNodes(graph, startNode, 200000);
        }
        if (goalNode == null) {
            goalNode = GraphNode.builder().position(goal).build();
            graph.nodes.add(goalNode);
            graph.adjacency.put(goalNode, new ArrayList<>());
            connectToNearbyNodes(graph, goalNode, 200000);
        }

        PriorityQueue<AStarNode> openSet = new PriorityQueue<>();
        Map<GraphNode, Double> gScore = new HashMap<>();
        Set<GraphNode> closedSet = new HashSet<>();

        double h0 = start.distanceTo(goal);
        openSet.add(new AStarNode(startNode, 0, h0, null));
        gScore.put(startNode, 0.0);

        int nodesExpanded = 0;
        final int MAX_NODES = 10000;

        while (!openSet.isEmpty() && nodesExpanded < MAX_NODES) {
            AStarNode current = openSet.poll();
            nodesExpanded++;

            if (current.node == goalNode ||
                    current.node.getPosition().distanceTo(goal) < 5000) {
                return buildResult(current, nodesExpanded, true);
            }

            if (closedSet.contains(current.node)) continue;
            closedSet.add(current.node);

            List<GraphEdge> edges = graph.adjacency.getOrDefault(current.node, Collections.emptyList());
            for (GraphEdge edge : edges) {
                if (!edge.isFeasible()) continue;
                GraphNode neighbor = edge.getTo();
                if (closedSet.contains(neighbor)) continue;

                double turnCost = 0;
                if (current.parent != null) {
                    turnCost = computeTurnCost(
                            current.parent.node.getPosition(),
                            current.node.getPosition(),
                            neighbor.getPosition());
                }

                double climbCost = computeClimbCost(
                        current.node.getPosition(),
                        neighbor.getPosition());

                double tentativeG = current.gCost + edge.getDistance() + turnCost + climbCost;

                if (tentativeG < gScore.getOrDefault(neighbor, Double.MAX_VALUE)) {
                    gScore.put(neighbor, tentativeG);
                    double h = neighbor.getPosition().distanceTo(goal);
                    openSet.add(new AStarNode(neighbor, tentativeG, h, current));
                }
            }
        }

        log.warn("A* failed after expanding {} nodes", nodesExpanded);
        return PathResult.builder()
                .found(false)
                .nodesExpanded(nodesExpanded)
                .waypoints(List.of(start, goal))
                .totalDistance(start.distanceTo(goal))
                .build();
    }

    private PathResult buildResult(AStarNode endNode, int nodesExpanded, boolean found) {
        LinkedList<Waypoint> path = new LinkedList<>();
        AStarNode cur = endNode;
        double totalDist = 0;
        Waypoint prevPos = null;

        while (cur != null) {
            Waypoint pos = cur.node.getPosition();
            path.addFirst(pos);
            if (prevPos != null) totalDist += pos.distanceTo(prevPos);
            prevPos = pos;
            cur = cur.parent;
        }

        List<Waypoint> smoothed = simplifyPath(path);

        log.info("A* path found: {} waypoints, {:.0f}m distance, {} nodes expanded",
                smoothed.size(), totalDist, nodesExpanded);

        return PathResult.builder()
                .waypoints(smoothed)
                .totalDistance(totalDist)
                .found(found)
                .nodesExpanded(nodesExpanded)
                .build();
    }

    private GraphNode findNodeNear(TangentGraph graph, Waypoint target, double thresholdMeters) {
        GraphNode best = null;
        double bestDist = Double.MAX_VALUE;
        for (GraphNode n : graph.nodes) {
            double d = n.getPosition().distanceTo(target);
            if (d < thresholdMeters && d < bestDist) {
                bestDist = d;
                best = n;
            }
        }
        return best;
    }

    private void connectToNearbyNodes(TangentGraph graph, GraphNode newNode, double maxDist) {
        for (GraphNode existing : graph.nodes) {
            if (existing == newNode) continue;
            double dist = newNode.getPosition().distanceTo(existing.getPosition());
            if (dist <= maxDist &&
                    TangentGraphBuilder.isEdgeFeasible(newNode.getPosition(), existing.getPosition(), graph.obstacles)) {
                graph.adjacency.computeIfAbsent(newNode, k -> new ArrayList<>())
                        .add(GraphEdge.builder().from(newNode).to(existing).distance(dist).feasible(true).build());
                graph.adjacency.computeIfAbsent(existing, k -> new ArrayList<>())
                        .add(GraphEdge.builder().from(existing).to(newNode).distance(dist).feasible(true).build());
            }
        }
    }

    private double computeTurnCost(Waypoint prev, Waypoint curr, Waypoint next) {
        double bearing1 = prev.bearingTo(curr);
        double bearing2 = curr.bearingTo(next);
        double turnAngle = Math.abs(normalizeAngle(bearing2 - bearing1));
        double maxTurnRate = 3.0;
        if (turnAngle > 120) return 50000;
        return turnAngle * 100;
    }

    private double computeClimbCost(Waypoint from, Waypoint to) {
        double alt1 = from.getAltitude() != null ? from.getAltitude() : 10000;
        double alt2 = to.getAltitude() != null ? to.getAltitude() : 10000;
        double deltaAlt = Math.abs(alt2 - alt1);
        double horizontal = from.distanceTo(to);
        if (horizontal < 1) return 0;
        double gradient = deltaAlt / horizontal;
        if (gradient > 0.15) return 100000;
        return deltaAlt * 0.5;
    }

    private double normalizeAngle(double angle) {
        while (angle > 180) angle -= 360;
        while (angle < -180) angle += 360;
        return angle;
    }

    private List<Waypoint> simplifyPath(List<Waypoint> path) {
        if (path.size() <= 3) return path;

        List<Waypoint> result = new ArrayList<>();
        result.add(path.get(0));

        int i = 1;
        while (i < path.size() - 1) {
            Waypoint prev = result.get(result.size() - 1);
            Waypoint curr = path.get(i);
            Waypoint next = path.get(i + 1);

            double bearing1 = prev.bearingTo(curr);
            double bearing2 = curr.bearingTo(next);
            double turn = Math.abs(normalizeAngle(bearing2 - bearing1));

            double alt1 = safeAlt(curr);
            double alt2 = safeAlt(next);
            double altChange = Math.abs(alt2 - alt1);

            if (turn > 5 || altChange > 300) {
                result.add(curr);
            }
            i++;
        }
        result.add(path.get(path.size() - 1));
        return result;
    }

    private double safeAlt(Waypoint w) {
        return w.getAltitude() != null ? w.getAltitude() : 10000;
    }
}
