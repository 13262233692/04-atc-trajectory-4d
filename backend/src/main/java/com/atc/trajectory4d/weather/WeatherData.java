package com.atc.trajectory4d.weather;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.time.LocalDateTime;

@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class WeatherData {

    private double latitude;
    private double longitude;
    private double altitude;

    private double windSpeedU;
    private double windSpeedV;
    private double windSpeed;
    private double windDirection;

    private double temperature;
    private double pressure;
    private double density;

    private LocalDateTime validTime;
    private String forecastSource;

    public double getWindSpeedAtAltitude(double targetAltitude) {
        double altitudeFactor = 1.0 + (targetAltitude - this.altitude) / 10000.0 * 0.3;
        return this.windSpeed * Math.max(0.5, altitudeFactor);
    }

    public double getTemperatureAtAltitude(double targetAltitude) {
        double lapseRate = 0.0065;
        return Math.max(216.65, this.temperature - lapseRate * (targetAltitude - this.altitude));
    }

    public double getWindComponentU(double targetAltitude) {
        double speed = getWindSpeedAtAltitude(targetAltitude);
        return speed * Math.cos(Math.toRadians(windDirection));
    }

    public double getWindComponentV(double targetAltitude) {
        double speed = getWindSpeedAtAltitude(targetAltitude);
        return speed * Math.sin(Math.toRadians(windDirection));
    }
}
