package com.atc.trajectory4d.websocket;

import com.atc.trajectory4d.model.TrajectoryPoint4D;
import com.atc.trajectory4d.service.TrajectoryService;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.messaging.handler.annotation.DestinationVariable;
import org.springframework.messaging.handler.annotation.MessageMapping;
import org.springframework.messaging.handler.annotation.SendTo;
import org.springframework.messaging.simp.annotation.SubscribeMapping;
import org.springframework.stereotype.Controller;

import java.security.Principal;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

@Slf4j
@Controller
@RequiredArgsConstructor
public class WebSocketController {

    private final WebSocketService webSocketService;
    private final TrajectoryService trajectoryService;

    @SubscribeMapping("/topic/trajectory/{flightId}")
    public List<TrajectoryPoint4D> handleSubscription(
            @DestinationVariable String flightId,
            Principal principal) {

        String sessionId = principal != null ? principal.getName() : "anonymous";
        webSocketService.subscribeFlight(sessionId, flightId);

        log.info("Client subscribed to trajectory: {}, session: {}", flightId, sessionId);

        List<TrajectoryPoint4D> trajectory = trajectoryService.getTrajectory(flightId);
        if (trajectory != null && !trajectory.isEmpty()) {
            int position = trajectoryService.getStreamPosition(flightId);
            if (position > 0) {
                return trajectory.subList(0, Math.min(position + 10, trajectory.size()));
            }
        }

        return trajectory;
    }

    @MessageMapping("/trajectory/stream/{flightId}")
    @SendTo("/topic/trajectory/{flightId}")
    public Map<String, Object> startStream(
            @DestinationVariable String flightId,
            Map<String, Object> payload,
            Principal principal) {

        String sessionId = principal != null ? principal.getName() : "anonymous";
        log.info("Stream request for flight {} from session {}", flightId, sessionId);

        boolean realtime = payload.containsKey("realtime") && (Boolean) payload.get("realtime");

        Map<String, Object> response = new HashMap<>();
        response.put("flightId", flightId);
        response.put("status", "STARTED");
        response.put("realtime", realtime);
        response.put("timestamp", System.currentTimeMillis());

        if (realtime) {
            trajectoryService.startRealtimeStreaming(flightId);
        } else {
            trajectoryService.startStreaming(flightId);
        }

        return response;
    }

    @MessageMapping("/trajectory/stop/{flightId}")
    @SendTo("/topic/trajectory/{flightId}")
    public Map<String, Object> stopStream(
            @DestinationVariable String flightId,
            Principal principal) {

        String sessionId = principal != null ? principal.getName() : "anonymous";
        log.info("Stop stream request for flight {} from session {}", flightId, sessionId);

        trajectoryService.stopStreaming(flightId);

        Map<String, Object> response = new HashMap<>();
        response.put("flightId", flightId);
        response.put("status", "STOPPED");
        response.put("timestamp", System.currentTimeMillis());

        return response;
    }

    @MessageMapping("/trajectory/current/{flightId}")
    @SendTo("/topic/trajectory/{flightId}")
    public TrajectoryPoint4D getCurrentPoint(
            @DestinationVariable String flightId,
            Principal principal) {

        return trajectoryService.getCurrentPoint(flightId);
    }

    @MessageMapping("/flight/list")
    @SendTo("/topic/flights")
    public List<String> getActiveFlights() {
        return trajectoryService.getActiveFlights();
    }

    @MessageMapping("/flight/status/{flightId}")
    @SendTo("/topic/status/{flightId}")
    public Map<String, Object> getFlightStatus(
            @DestinationVariable String flightId) {

        Map<String, Object> status = new HashMap<>();
        status.put("flightId", flightId);
        status.put("pointCount", trajectoryService.getTrajectoryPointCount(flightId));
        status.put("streamPosition", trajectoryService.getStreamPosition(flightId));
        status.put("currentPoint", trajectoryService.getCurrentPoint(flightId));
        status.put("flightPlan", trajectoryService.getFlightPlan(flightId));
        status.put("timestamp", System.currentTimeMillis());

        return status;
    }

    @MessageMapping("/weather/subscribe/{area}")
    public void subscribeWeather(
            @DestinationVariable String area,
            Principal principal) {

        String sessionId = principal != null ? principal.getName() : "anonymous";
        log.info("Session {} subscribed to weather area: {}", sessionId, area);
    }

    @MessageMapping("/ping")
    @SendTo("/topic/pong")
    public Map<String, Object> handlePing(Map<String, Object> payload) {
        Map<String, Object> response = new HashMap<>();
        response.put("timestamp", System.currentTimeMillis());
        response.put("clientTime", payload.get("timestamp"));
        return response;
    }
}
