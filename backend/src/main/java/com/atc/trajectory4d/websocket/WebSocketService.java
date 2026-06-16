package com.atc.trajectory4d.websocket;

import com.atc.trajectory4d.config.AppProperties;
import com.atc.trajectory4d.model.TrajectoryPoint4D;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.messaging.simp.SimpMessagingTemplate;
import org.springframework.stereotype.Service;

import java.util.Map;
import java.util.Set;
import java.util.concurrent.ConcurrentHashMap;

@Slf4j
@Service
@RequiredArgsConstructor
public class WebSocketService {

    private final SimpMessagingTemplate messagingTemplate;
    private final AppProperties appProperties;

    private final Map<String, Set<String>> flightSubscribers = new ConcurrentHashMap<>();
    private final Map<String, Long> lastSentTimestamps = new ConcurrentHashMap<>();

    public void sendTrajectoryPoint(String flightId, TrajectoryPoint4D point) {
        String destination = appProperties.getWebsocket().getTopicPrefix() + "/trajectory/" + flightId;

        try {
            messagingTemplate.convertAndSend(destination, point);
            lastSentTimestamps.put(flightId, System.currentTimeMillis());
            log.debug("Sent trajectory point for flight {} to topic {}", flightId, destination);
        } catch (Exception e) {
            log.error("Failed to send trajectory point for flight {}", flightId, e);
        }
    }

    public void sendTrajectoryUpdate(String flightId, TrajectoryUpdate update) {
        String destination = appProperties.getWebsocket().getTopicPrefix() + "/trajectory/" + flightId;

        try {
            messagingTemplate.convertAndSend(destination, update);
            log.debug("Sent trajectory update for flight {} to topic {}", flightId, destination);
        } catch (Exception e) {
            log.error("Failed to send trajectory update for flight {}", flightId, e);
        }
    }

    public void broadcastTrajectoryPoint(TrajectoryPoint4D point) {
        String destination = appProperties.getWebsocket().getTopicPrefix() + "/trajectory/all";

        try {
            messagingTemplate.convertAndSend(destination, point);
            log.debug("Broadcasted trajectory point for flight {}", point.getFlightId());
        } catch (Exception e) {
            log.error("Failed to broadcast trajectory point for flight {}", point.getFlightId(), e);
        }
    }

    public void sendFlightStatus(String flightId, FlightStatus status) {
        String destination = appProperties.getWebsocket().getTopicPrefix() + "/status/" + flightId;

        try {
            messagingTemplate.convertAndSend(destination, status);
            log.debug("Sent flight status for flight {}: {}", flightId, status.getStatus());
        } catch (Exception e) {
            log.error("Failed to send flight status for flight {}", flightId, e);
        }
    }

    public void sendWeatherUpdate(String area, WeatherData weatherData) {
        String destination = appProperties.getWebsocket().getTopicPrefix() + "/weather/" + area;

        try {
            messagingTemplate.convertAndSend(destination, weatherData);
            log.debug("Sent weather update for area {}", area);
        } catch (Exception e) {
            log.error("Failed to send weather update for area {}", area, e);
        }
    }

    public void sendNotification(String userId, Notification notification) {
        String destination = appProperties.getWebsocket().getTopicPrefix() + "/notifications/" + userId;

        try {
            messagingTemplate.convertAndSend(destination, notification);
            log.debug("Sent notification to user {}", userId);
        } catch (Exception e) {
            log.error("Failed to send notification to user {}", userId, e);
        }
    }

    public void broadcastNotification(Notification notification) {
        String destination = appProperties.getWebsocket().getTopicPrefix() + "/notifications/all";

        try {
            messagingTemplate.convertAndSend(destination, notification);
            log.debug("Broadcasted notification: {}", notification.getTitle());
        } catch (Exception e) {
            log.error("Failed to broadcast notification", e);
        }
    }

    public void broadcastNotification(String message) {
        String destination = appProperties.getWebsocket().getTopicPrefix() + "/notifications";
        try {
            messagingTemplate.convertAndSend(destination, message);
            log.debug("Broadcasted raw notification: {}", message);
        } catch (Exception e) {
            log.error("Failed to broadcast raw notification", e);
        }
    }

    public void subscribeFlight(String sessionId, String flightId) {
        flightSubscribers.computeIfAbsent(flightId, k -> ConcurrentHashMap.newKeySet())
                .add(sessionId);
        log.info("Session {} subscribed to flight {}", sessionId, flightId);
    }

    public void unsubscribeFlight(String sessionId, String flightId) {
        Set<String> subscribers = flightSubscribers.get(flightId);
        if (subscribers != null) {
            subscribers.remove(sessionId);
            if (subscribers.isEmpty()) {
                flightSubscribers.remove(flightId);
            }
        }
        log.info("Session {} unsubscribed from flight {}", sessionId, flightId);
    }

    public int getSubscriberCount(String flightId) {
        Set<String> subscribers = flightSubscribers.get(flightId);
        return subscribers != null ? subscribers.size() : 0;
    }

    public boolean hasSubscribers(String flightId) {
        return getSubscriberCount(flightId) > 0;
    }

    public long getLastSentTimestamp(String flightId) {
        return lastSentTimestamps.getOrDefault(flightId, 0L);
    }

    public void clearFlightState(String flightId) {
        flightSubscribers.remove(flightId);
        lastSentTimestamps.remove(flightId);
        log.info("Cleared WebSocket state for flight {}", flightId);
    }

    @lombok.Data
    @lombok.Builder
    @lombok.NoArgsConstructor
    @lombok.AllArgsConstructor
    public static class TrajectoryUpdate {
        private String flightId;
        private String updateType;
        private Object data;
        private long timestamp;
    }

    @lombok.Data
    @lombok.Builder
    @lombok.NoArgsConstructor
    @lombok.AllArgsConstructor
    public static class FlightStatus {
        private String flightId;
        private String status;
        private String message;
        private TrajectoryPoint4D currentPosition;
        private double progress;
        private long timestamp;
    }

    @lombok.Data
    @lombok.Builder
    @lombok.NoArgsConstructor
    @lombok.AllArgsConstructor
    public static class WeatherData {
        private double latitude;
        private double longitude;
        private double altitude;
        private double windSpeed;
        private double windDirection;
        private double temperature;
        private long timestamp;
    }

    @lombok.Data
    @lombok.Builder
    @lombok.NoArgsConstructor
    @lombok.AllArgsConstructor
    public static class Notification {
        private String id;
        private String type;
        private String title;
        private String message;
        private String severity;
        private long timestamp;
    }
}
