package com.atc.trajectory4d.weather;

import com.atc.trajectory4d.config.AppProperties;
import jakarta.annotation.PostConstruct;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.apache.commons.csv.CSVFormat;
import org.apache.commons.csv.CSVParser;
import org.apache.commons.csv.CSVRecord;
import org.springframework.stereotype.Service;

import java.io.FileReader;
import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;
import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.CopyOnWriteArrayList;

@Slf4j
@Service
@RequiredArgsConstructor
public class WeatherService {

    private final AppProperties appProperties;
    private final List<WeatherData> weatherGrid = new CopyOnWriteArrayList<>();

    @PostConstruct
    public void init() {
        loadWeatherData();
    }

    public void loadWeatherData() {
        Path weatherPath = Paths.get(appProperties.getWeather().getDataPath());
        if (!Files.exists(weatherPath)) {
            log.warn("Weather data path not found: {}, using default weather model", weatherPath.toAbsolutePath());
            generateDefaultWeatherGrid();
            return;
        }

        try {
            Files.list(weatherPath)
                    .filter(path -> path.toString().endsWith(".csv"))
                    .forEach(this::parseWeatherFile);
            log.info("Loaded {} weather grid points", weatherGrid.size());
        } catch (IOException e) {
            log.error("Failed to load weather data, using default model", e);
            generateDefaultWeatherGrid();
        }

        if (weatherGrid.isEmpty()) {
            generateDefaultWeatherGrid();
        }
    }

    private void parseWeatherFile(Path filePath) {
        try (FileReader reader = new FileReader(filePath.toFile());
             CSVParser csvParser = new CSVParser(reader, CSVFormat.DEFAULT
                     .withFirstRecordAsHeader()
                     .withIgnoreHeaderCase()
                     .withTrim())) {

            DateTimeFormatter formatter = DateTimeFormatter.ofPattern("yyyy-MM-dd HH:mm:ss");

            for (CSVRecord record : csvParser) {
                try {
                    WeatherData data = WeatherData.builder()
                            .latitude(Double.parseDouble(record.get("LATITUDE")))
                            .longitude(Double.parseDouble(record.get("LONGITUDE")))
                            .altitude(Double.parseDouble(record.get("ALTITUDE")))
                            .windSpeedU(Double.parseDouble(record.get("WIND_U")))
                            .windSpeedV(Double.parseDouble(record.get("WIND_V")))
                            .windSpeed(Double.parseDouble(record.get("WIND_SPEED")))
                            .windDirection(Double.parseDouble(record.get("WIND_DIRECTION")))
                            .temperature(Double.parseDouble(record.get("TEMPERATURE")))
                            .pressure(Double.parseDouble(record.get("PRESSURE")))
                            .density(Double.parseDouble(record.get("DENSITY")))
                            .validTime(LocalDateTime.parse(record.get("VALID_TIME"), formatter))
                            .forecastSource(record.get("SOURCE"))
                            .build();
                    weatherGrid.add(data);
                } catch (Exception e) {
                    log.warn("Skipping invalid weather record: {}", record, e);
                }
            }
        } catch (IOException e) {
            log.error("Failed to parse weather file: {}", filePath, e);
        }
    }

    private void generateDefaultWeatherGrid() {
        weatherGrid.clear();
        log.info("Generating default weather grid");

        double[] latitudes = {20.0, 25.0, 30.0, 35.0, 40.0, 45.0, 50.0};
        double[] longitudes = {100.0, 105.0, 110.0, 115.0, 120.0, 125.0, 130.0};
        double[] altitudes = {0.0, 3000.0, 6000.0, 9000.0, 12000.0, 15000.0};

        for (double lat : latitudes) {
            for (double lon : longitudes) {
                for (double alt : altitudes) {
                    WeatherData data = generateWeatherPoint(lat, lon, alt);
                    weatherGrid.add(data);
                }
            }
        }

        log.info("Generated {} default weather grid points", weatherGrid.size());
    }

    private WeatherData generateWeatherPoint(double latitude, double longitude, double altitude) {
        double baseWindSpeed = 15.0 + altitude / 1000.0 * 3.0;
        double windDirection = 270.0 + Math.sin(latitude * Math.PI / 180.0) * 30.0
                + Math.cos(longitude * Math.PI / 180.0) * 20.0;

        double temperature = 288.15 - 0.0065 * altitude;
        if (altitude > 11000.0) {
            temperature = 216.65;
        }

        double pressure = 101325.0 * Math.pow(1 - 0.0065 * altitude / 288.15, 5.25588);
        double density = pressure / (287.0 * temperature);

        double windU = baseWindSpeed * Math.cos(Math.toRadians(windDirection));
        double windV = baseWindSpeed * Math.sin(Math.toRadians(windDirection));

        return WeatherData.builder()
                .latitude(latitude)
                .longitude(longitude)
                .altitude(altitude)
                .windSpeedU(windU)
                .windSpeedV(windV)
                .windSpeed(baseWindSpeed)
                .windDirection(windDirection)
                .temperature(temperature)
                .pressure(pressure)
                .density(density)
                .validTime(LocalDateTime.now())
                .forecastSource("DEFAULT_MODEL")
                .build();
    }

    public WeatherData getWeatherAt(double latitude, double longitude, double altitude) {
        if (weatherGrid.size() < 8) {
            return generateWeatherPoint(latitude, longitude, altitude);
        }

        List<WeatherData> nearbyPoints = findNearbyPoints(latitude, longitude, altitude, 4);

        if (nearbyPoints.isEmpty()) {
            return generateWeatherPoint(latitude, longitude, altitude);
        }

        return interpolateWeather(nearbyPoints, latitude, longitude, altitude);
    }

    private List<WeatherData> findNearbyPoints(double latitude, double longitude, double altitude, int count) {
        List<WeatherData> sorted = new ArrayList<>(weatherGrid);
        sorted.sort((a, b) -> {
            double distA = calculate3DDistance(a, latitude, longitude, altitude);
            double distB = calculate3DDistance(b, latitude, longitude, altitude);
            return Double.compare(distA, distB);
        });
        return sorted.subList(0, Math.min(count, sorted.size()));
    }

    private double calculate3DDistance(WeatherData point, double lat, double lon, double alt) {
        double latDiff = (point.getLatitude() - lat) * 111000.0;
        double lonDiff = (point.getLongitude() - lon) * 111000.0 * Math.cos(Math.toRadians(lat));
        double altDiff = point.getAltitude() - alt;
        return Math.sqrt(latDiff * latDiff + lonDiff * lonDiff + altDiff * altDiff);
    }

    private WeatherData interpolateWeather(List<WeatherData> points, double lat, double lon, double alt) {
        double totalWeight = 0.0;
        double windU = 0.0, windV = 0.0, windSpeed = 0.0, windDir = 0.0;
        double temp = 0.0, pressure = 0.0, density = 0.0;

        for (WeatherData point : points) {
            double distance = calculate3DDistance(point, lat, lon, alt);
            double weight = 1.0 / (distance + 1000.0);
            totalWeight += weight;

            windU += point.getWindSpeedU() * weight;
            windV += point.getWindSpeedV() * weight;
            windSpeed += point.getWindSpeed() * weight;
            windDir += point.getWindDirection() * weight;
            temp += point.getTemperature() * weight;
            pressure += point.getPressure() * weight;
            density += point.getDensity() * weight;
        }

        if (totalWeight > 0) {
            windU /= totalWeight;
            windV /= totalWeight;
            windSpeed /= totalWeight;
            windDir /= totalWeight;
            temp /= totalWeight;
            pressure /= totalWeight;
            density /= totalWeight;
        }

        return WeatherData.builder()
                .latitude(lat)
                .longitude(lon)
                .altitude(alt)
                .windSpeedU(windU)
                .windSpeedV(windV)
                .windSpeed(windSpeed)
                .windDirection(windDir)
                .temperature(temp)
                .pressure(pressure)
                .density(density)
                .validTime(LocalDateTime.now())
                .forecastSource("INTERPOLATED")
                .build();
    }

    public double getWindSpeedAt(double latitude, double longitude, double altitude) {
        return getWeatherAt(latitude, longitude, altitude).getWindSpeedAtAltitude(altitude);
    }

    public double getWindDirectionAt(double latitude, double longitude, double altitude) {
        return getWeatherAt(latitude, longitude, altitude).getWindDirection();
    }

    public double getTemperatureAt(double latitude, double longitude, double altitude) {
        return getWeatherAt(latitude, longitude, altitude).getTemperatureAtAltitude(altitude);
    }

    public void reloadWeatherData() {
        weatherGrid.clear();
        loadWeatherData();
    }

    public List<WeatherData> getAllWeatherData() {
        return new ArrayList<>(weatherGrid);
    }
}
