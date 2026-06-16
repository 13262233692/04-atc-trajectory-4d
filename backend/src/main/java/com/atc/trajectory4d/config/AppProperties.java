package com.atc.trajectory4d.config;

import lombok.Data;
import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.context.annotation.Configuration;

@Data
@Configuration
@ConfigurationProperties(prefix = "app")
public class AppProperties {

    private Bada bada = new Bada();
    private Trajectory trajectory = new Trajectory();
    private Websocket websocket = new Websocket();
    private Weather weather = new Weather();
    private FlightPlan flightPlan = new FlightPlan();

    @Data
    public static class Bada {
        private String dataPath;
    }

    @Data
    public static class Trajectory {
        private double timeStep = 5.0;
        private int maxIteration = 10000;
    }

    @Data
    public static class Websocket {
        private String topicPrefix = "/topic";
        private String appPrefix = "/app";
        private String endpoint = "/ws";
    }

    @Data
    public static class Weather {
        private String dataPath;
    }

    @Data
    public static class FlightPlan {
        private String dataPath;
    }
}
