package com.atc.trajectory4d.controller;

import com.atc.trajectory4d.model.dto.ApiResponse;
import com.atc.trajectory4d.weather.WeatherData;
import com.atc.trajectory4d.weather.WeatherService;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import java.util.HashMap;
import java.util.List;
import java.util.Map;

@Slf4j
@RestController
@RequestMapping("/weather")
@RequiredArgsConstructor
public class WeatherController {

    private final WeatherService weatherService;

    @GetMapping("/at")
    public ResponseEntity<ApiResponse<WeatherData>> getWeatherAt(
            @RequestParam double latitude,
            @RequestParam double longitude,
            @RequestParam(defaultValue = "0") double altitude) {

        WeatherData weather = weatherService.getWeatherAt(latitude, longitude, altitude);
        return ResponseEntity.ok(ApiResponse.success(weather));
    }

    @GetMapping("/wind-speed")
    public ResponseEntity<ApiResponse<Map<String, Object>>> getWindSpeedAt(
            @RequestParam double latitude,
            @RequestParam double longitude,
            @RequestParam(defaultValue = "0") double altitude) {

        double windSpeed = weatherService.getWindSpeedAt(latitude, longitude, altitude);
        double windDirection = weatherService.getWindDirectionAt(latitude, longitude, altitude);

        Map<String, Object> result = new HashMap<>();
        result.put("latitude", latitude);
        result.put("longitude", longitude);
        result.put("altitude", altitude);
        result.put("windSpeed", windSpeed);
        result.put("windSpeedKnots", windSpeed * 1.94384);
        result.put("windDirection", windDirection);

        return ResponseEntity.ok(ApiResponse.success(result));
    }

    @GetMapping("/temperature")
    public ResponseEntity<ApiResponse<Map<String, Object>>> getTemperatureAt(
            @RequestParam double latitude,
            @RequestParam double longitude,
            @RequestParam(defaultValue = "0") double altitude) {

        double temperature = weatherService.getTemperatureAt(latitude, longitude, altitude);

        Map<String, Object> result = new HashMap<>();
        result.put("latitude", latitude);
        result.put("longitude", longitude);
        result.put("altitude", altitude);
        result.put("temperatureKelvin", temperature);
        result.put("temperatureCelsius", temperature - 273.15);
        result.put("temperatureFahrenheit", (temperature - 273.15) * 9/5 + 32);

        return ResponseEntity.ok(ApiResponse.success(result));
    }

    @GetMapping("/all")
    public ResponseEntity<ApiResponse<List<WeatherData>>> getAllWeatherData() {
        List<WeatherData> allData = weatherService.getAllWeatherData();
        return ResponseEntity.ok(ApiResponse.success(
                String.format("Found %d weather grid points", allData.size()),
                allData
        ));
    }

    @PostMapping("/reload")
    public ResponseEntity<ApiResponse<Map<String, Object>>> reloadWeatherData() {
        weatherService.reloadWeatherData();

        Map<String, Object> result = new HashMap<>();
        result.put("gridPointCount", weatherService.getAllWeatherData().size());

        return ResponseEntity.ok(ApiResponse.success("Weather data reloaded", result));
    }
}
