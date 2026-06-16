package com.atc.trajectory4d.service;

import com.atc.trajectory4d.model.FlightPlan;
import com.atc.trajectory4d.model.TrajectoryPoint4D;
import com.atc.trajectory4d.trajectory.TrajectoryCalculator;
import com.atc.trajectory4d.websocket.WebSocketService;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.scheduling.annotation.Async;
import org.springframework.stereotype.Service;

import java.util.List;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;

@Slf4j
@Service
@RequiredArgsConstructor
public class TrajectoryService {

    private final TrajectoryCalculator trajectoryCalculator;
    private final WebSocketService webSocketService;

    private final Map<String, List<TrajectoryPoint4D>> trajectoryCache = new ConcurrentHashMap<>();
    private final Map<String, FlightPlan> flightPlanCache = new ConcurrentHashMap<>();
    private final Map<String, Integer> streamPositions = new ConcurrentHashMap<>();

    public List<TrajectoryPoint4D> calculateTrajectory(FlightPlan flightPlan) {
        log.info("Calculating trajectory for flight: {}", flightPlan.getFlightId());

        List<TrajectoryPoint4D> trajectory = trajectoryCalculator.calculate4DTrajectory(flightPlan);

        trajectoryCache.put(flightPlan.getFlightId(), trajectory);
        flightPlanCache.put(flightPlan.getFlightId(), flightPlan);

        log.info("Trajectory calculated with {} points for flight {}",
                trajectory.size(), flightPlan.getFlightId());

        return trajectory;
    }

    @Async
    public void streamTrajectory(String flightId) {
        List<TrajectoryPoint4D> trajectory = trajectoryCache.get(flightId);
        if (trajectory == null || trajectory.isEmpty()) {
            log.warn("No trajectory found for flight {}", flightId);
            return;
        }

        log.info("Starting trajectory stream for flight {}", flightId);

        int startIndex = streamPositions.getOrDefault(flightId, 0);

        for (int i = startIndex; i < trajectory.size(); i++) {
            TrajectoryPoint4D point = trajectory.get(i);
            webSocketService.sendTrajectoryPoint(flightId, point);
            streamPositions.put(flightId, i + 1);

            try {
                Thread.sleep(100);
            } catch (InterruptedException e) {
                Thread.currentThread().interrupt();
                log.warn("Trajectory stream interrupted for flight {}", flightId);
                return;
            }
        }

        log.info("Trajectory stream completed for flight {}", flightId);
    }

    @Async
    public void streamTrajectoryRealtime(String flightId) {
        List<TrajectoryPoint4D> trajectory = trajectoryCache.get(flightId);
        if (trajectory == null || trajectory.isEmpty()) {
            log.warn("No trajectory found for flight {}", flightId);
            return;
        }

        log.info("Starting realtime trajectory stream for flight {}", flightId);

        int startIndex = streamPositions.getOrDefault(flightId, 0);

        for (int i = startIndex; i < trajectory.size(); i++) {
            TrajectoryPoint4D point = trajectory.get(i);
            webSocketService.sendTrajectoryPoint(flightId, point);
            streamPositions.put(flightId, i + 1);

            try {
                Thread.sleep(5000);
            } catch (InterruptedException e) {
                Thread.currentThread().interrupt();
                log.warn("Realtime trajectory stream interrupted for flight {}", flightId);
                return;
            }
        }

        log.info("Realtime trajectory stream completed for flight {}", flightId);
    }

    public void startStreaming(String flightId) {
        streamPositions.put(flightId, 0);
        streamTrajectory(flightId);
    }

    public void startRealtimeStreaming(String flightId) {
        streamPositions.put(flightId, 0);
        streamTrajectoryRealtime(flightId);
    }

    public void stopStreaming(String flightId) {
        streamPositions.remove(flightId);
    }

    public List<TrajectoryPoint4D> getTrajectory(String flightId) {
        return trajectoryCache.get(flightId);
    }

    public FlightPlan getFlightPlan(String flightId) {
        return flightPlanCache.get(flightId);
    }

    public TrajectoryPoint4D getTrajectoryPointAtTime(String flightId, long timestampSeconds) {
        List<TrajectoryPoint4D> trajectory = trajectoryCache.get(flightId);
        if (trajectory == null || trajectory.isEmpty()) {
            return null;
        }

        FlightPlan flightPlan = flightPlanCache.get(flightId);
        if (flightPlan == null) {
            return trajectory.get(0);
        }

        long startTime = java.time.Duration.between(
                java.time.LocalDateTime.of(1970, 1, 1, 0, 0),
                flightPlan.getDepartureTime()
        ).getSeconds();

        long elapsed = timestampSeconds - startTime;
        int index = (int) (elapsed / 5);

        if (index < 0) return trajectory.get(0);
        if (index >= trajectory.size()) return trajectory.get(trajectory.size() - 1);

        return trajectory.get(index);
    }

    public TrajectoryPoint4D getCurrentPoint(String flightId) {
        List<TrajectoryPoint4D> trajectory = trajectoryCache.get(flightId);
        if (trajectory == null || trajectory.isEmpty()) {
            return null;
        }

        int position = streamPositions.getOrDefault(flightId, 0);
        if (position >= trajectory.size()) {
            return trajectory.get(trajectory.size() - 1);
        }

        return trajectory.get(position);
    }

    public boolean deleteTrajectory(String flightId) {
        stopStreaming(flightId);
        trajectoryCache.remove(flightId);
        flightPlanCache.remove(flightId);
        return true;
    }

    public List<String> getActiveFlights() {
        return List.copyOf(trajectoryCache.keySet());
    }

    public int getTrajectoryPointCount(String flightId) {
        List<TrajectoryPoint4D> trajectory = trajectoryCache.get(flightId);
        return trajectory != null ? trajectory.size() : 0;
    }

    public int getStreamPosition(String flightId) {
        return streamPositions.getOrDefault(flightId, 0);
    }
}
