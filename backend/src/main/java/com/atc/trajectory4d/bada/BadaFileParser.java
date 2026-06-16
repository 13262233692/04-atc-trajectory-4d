package com.atc.trajectory4d.bada;

import com.atc.trajectory4d.bada.model.AircraftPerformance;
import com.atc.trajectory4d.config.AppProperties;
import jakarta.annotation.PostConstruct;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.apache.commons.csv.CSVFormat;
import org.apache.commons.csv.CSVParser;
import org.apache.commons.csv.CSVRecord;
import org.springframework.stereotype.Component;

import java.io.FileReader;
import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.*;
import java.util.concurrent.ConcurrentHashMap;

@Slf4j
@Component
@RequiredArgsConstructor
public class BadaFileParser {

    private final AppProperties appProperties;
    private final Map<String, AircraftPerformance> performanceCache = new ConcurrentHashMap<>();

    @PostConstruct
    public void init() {
        loadAllBadaData();
    }

    public void loadAllBadaData() {
        Path badaPath = Paths.get(appProperties.getBada().getDataPath());
        if (!Files.exists(badaPath)) {
            log.warn("BADA data path not found: {}", badaPath.toAbsolutePath());
            return;
        }

        try {
            Files.list(badaPath)
                    .filter(path -> path.toString().endsWith(".csv") || path.toString().endsWith(".bada"))
                    .forEach(this::parseBadaFile);
            log.info("Loaded {} aircraft performance models from BADA data", performanceCache.size());
        } catch (IOException e) {
            log.error("Failed to load BADA data", e);
        }
    }

    public AircraftPerformance parseBadaFile(Path filePath) {
        try (FileReader reader = new FileReader(filePath.toFile());
             CSVParser csvParser = new CSVParser(reader, CSVFormat.DEFAULT
                     .withFirstRecordAsHeader()
                     .withIgnoreHeaderCase()
                     .withTrim())) {

            String aircraftType = filePath.getFileName().toString().replace(".csv", "").replace(".bada", "");
            AircraftPerformance performance = parsePerformanceData(csvParser, aircraftType);
            performanceCache.put(aircraftType, performance);
            log.debug("Loaded BADA data for aircraft type: {}", aircraftType);
            return performance;

        } catch (IOException e) {
            log.error("Failed to parse BADA file: {}", filePath, e);
            throw new RuntimeException("Failed to parse BADA file: " + filePath, e);
        }
    }

    private AircraftPerformance parsePerformanceData(CSVParser csvParser, String aircraftType) {
        AircraftPerformance.AircraftPerformanceBuilder builder = AircraftPerformance.builder()
                .aircraftType(aircraftType);

        AircraftPerformance.DragPolar.DragPolarBuilder dragPolarBuilder = AircraftPerformance.DragPolar.builder();
        AircraftPerformance.ThrustModel.ThrustModelBuilder thrustBuilder = AircraftPerformance.ThrustModel.builder();
        AircraftPerformance.FuelFlowModel.FuelFlowModelBuilder fuelFlowBuilder = AircraftPerformance.FuelFlowModel.builder();
        AircraftPerformance.ClimbPerformance.ClimbPerformanceBuilder climbBuilder = AircraftPerformance.ClimbPerformance.builder();
        AircraftPerformance.CruisePerformance.CruisePerformanceBuilder cruiseBuilder = AircraftPerformance.CruisePerformance.builder();
        AircraftPerformance.DescentPerformance.DescentPerformanceBuilder descentBuilder = AircraftPerformance.DescentPerformance.builder();

        Map<String, Double> climbRates = new HashMap<>();
        Map<String, Double> fuelFlows = new HashMap<>();
        Map<String, Double> descentRates = new HashMap<>();

        for (CSVRecord record : csvParser) {
            String parameter = record.get("PARAMETER");
            String value = record.get("VALUE");
            String phase = record.isMapped("PHASE") ? record.get("PHASE") : "";
            String condition = record.isMapped("CONDITION") ? record.get("CONDITION") : "";

            parseBasicParameters(builder, parameter, value);
            parseAerodynamicParameters(dragPolarBuilder, parameter, value);
            parseThrustParameters(thrustBuilder, parameter, value);
            parseFuelFlowParameters(fuelFlowBuilder, parameter, value);
            parseClimbParameters(climbBuilder, parameter, value, condition, climbRates);
            parseCruiseParameters(cruiseBuilder, parameter, value, condition, fuelFlows);
            parseDescentParameters(descentBuilder, parameter, value, condition, descentRates);
        }

        climbBuilder.climbRatesByAltitude(climbRates);
        cruiseBuilder.fuelFlowByAltitude(fuelFlows);
        descentBuilder.descentRatesByAltitude(descentRates);

        return builder
                .dragPolar(dragPolarBuilder.build())
                .thrustModel(thrustBuilder.build())
                .fuelFlowModel(fuelFlowBuilder.build())
                .climbPerformance(climbBuilder.build())
                .cruisePerformance(cruiseBuilder.build())
                .descentPerformance(descentBuilder.build())
                .build();
    }

    private void parseBasicParameters(AircraftPerformance.AircraftPerformanceBuilder builder,
                                      String parameter, String value) {
        switch (parameter.toUpperCase()) {
            case "ENGINE_TYPE" -> builder.engineType(value);
            case "NUMBER_OF_ENGINES" -> builder.numberOfEngines(Integer.parseInt(value));
            case "REFERENCE_MASS" -> builder.referenceMass(Double.parseDouble(value));
            case "MAX_TAKEOFF_MASS" -> builder.maxTakeoffMass(Double.parseDouble(value));
            case "MAX_LANDING_MASS" -> builder.maxLandingMass(Double.parseDouble(value));
            case "MAX_ZERO_FUEL_MASS" -> builder.maxZeroFuelMass(Double.parseDouble(value));
            case "OPERATING_EMPTY_MASS" -> builder.operatingEmptyMass(Double.parseDouble(value));
            case "WING_AREA" -> builder.wingArea(Double.parseDouble(value));
            case "WING_SPAN" -> builder.wingSpan(Double.parseDouble(value));
            case "WING_ASPECT_RATIO" -> builder.wingAspectRatio(Double.parseDouble(value));
            case "MAX_OPERATING_MACH" -> builder.maxOperatingMach(Double.parseDouble(value));
            case "MAX_OPERATING_SPEED" -> builder.maxOperatingSpeed(Double.parseDouble(value));
            case "CRUISE_MACH" -> builder.cruiseMach(Double.parseDouble(value));
            case "CRUISE_SPEED" -> builder.cruiseSpeed(Double.parseDouble(value));
            case "SERVICE_CEILING" -> builder.serviceCeiling(Double.parseDouble(value));
            case "MAX_ALTITUDE" -> builder.maxAltitude(Double.parseDouble(value));
            case "STALL_SPEED_LANDING" -> builder.stallSpeedLanding(Double.parseDouble(value));
            case "STALL_SPEED_TAKEOFF" -> builder.stallSpeedTakeoff(Double.parseDouble(value));
        }
    }

    private void parseAerodynamicParameters(AircraftPerformance.DragPolar.DragPolarBuilder builder,
                                            String parameter, String value) {
        switch (parameter.toUpperCase()) {
            case "CD0" -> builder.cd0(Double.parseDouble(value));
            case "CD2" -> builder.cd2(Double.parseDouble(value));
            case "CD4" -> builder.cd4(Double.parseDouble(value));
            case "K" -> builder.k(Double.parseDouble(value));
            case "MACH_DRAG_INDEX" -> builder.machDragIndex(Double.parseDouble(value));
        }
    }

    private void parseThrustParameters(AircraftPerformance.ThrustModel.ThrustModelBuilder builder,
                                       String parameter, String value) {
        switch (parameter.toUpperCase()) {
            case "MAX_TAKEOFF_THRUST" -> builder.maxTakeoffThrust(Double.parseDouble(value));
            case "MAX_CLIMB_THRUST" -> builder.maxClimbThrust(Double.parseDouble(value));
            case "MAX_CRUISE_THRUST" -> builder.maxCruiseThrust(Double.parseDouble(value));
            case "THRUST_ALTITUDE_FACTOR" -> builder.thrustAltitudeFactor(Double.parseDouble(value));
            case "THRUST_MACH_FACTOR" -> builder.thrustMachFactor(Double.parseDouble(value));
            case "THRUST_TEMPERATURE_FACTOR" -> builder.thrustTemperatureFactor(Double.parseDouble(value));
        }
    }

    private void parseFuelFlowParameters(AircraftPerformance.FuelFlowModel.FuelFlowModelBuilder builder,
                                         String parameter, String value) {
        switch (parameter.toUpperCase()) {
            case "CF1" -> builder.cf1(Double.parseDouble(value));
            case "CF2" -> builder.cf2(Double.parseDouble(value));
            case "CR1" -> builder.cr1(Double.parseDouble(value));
            case "CR2" -> builder.cr2(Double.parseDouble(value));
            case "CD1" -> builder.cd1(Double.parseDouble(value));
            case "CD2" -> builder.cd2(Double.parseDouble(value));
            case "IDLE_FUEL_FLOW" -> builder.idleFuelFlow(Double.parseDouble(value));
            case "FUEL_DENSITY" -> builder.fuelDensity(Double.parseDouble(value));
        }
    }

    private void parseClimbParameters(AircraftPerformance.ClimbPerformance.ClimbPerformanceBuilder builder,
                                      String parameter, String value, String condition,
                                      Map<String, Double> climbRates) {
        switch (parameter.toUpperCase()) {
            case "INITIAL_CLIMB_RATE" -> builder.initialClimbRate(Double.parseDouble(value));
            case "CLIMB_SPEED" -> builder.climbSpeed(Double.parseDouble(value));
            case "CLIMB_MACH" -> builder.climbMach(Double.parseDouble(value));
            case "TRANSITION_ALTITUDE" -> builder.transitionAltitude(Double.parseDouble(value));
            case "CLIMB_RATE" -> {
                if (!condition.isEmpty()) {
                    climbRates.put(condition, Double.parseDouble(value));
                }
            }
        }
    }

    private void parseCruiseParameters(AircraftPerformance.CruisePerformance.CruisePerformanceBuilder builder,
                                       String parameter, String value, String condition,
                                       Map<String, Double> fuelFlows) {
        switch (parameter.toUpperCase()) {
            case "OPTIMUM_CRUISE_ALTITUDE" -> builder.optimumCruiseAltitude(Double.parseDouble(value));
            case "MAXIMUM_CRUISE_ALTITUDE" -> builder.maximumCruiseAltitude(Double.parseDouble(value));
            case "FUEL_FLOW_AT_CRUISE" -> builder.fuelFlowAtCruise(Double.parseDouble(value));
            case "FUEL_FLOW" -> {
                if (!condition.isEmpty()) {
                    fuelFlows.put(condition, Double.parseDouble(value));
                }
            }
        }
    }

    private void parseDescentParameters(AircraftPerformance.DescentPerformance.DescentPerformanceBuilder builder,
                                        String parameter, String value, String condition,
                                        Map<String, Double> descentRates) {
        switch (parameter.toUpperCase()) {
            case "DESCENT_RATE" -> builder.descentRate(Double.parseDouble(value));
            case "DESCENT_SPEED" -> builder.descentSpeed(Double.parseDouble(value));
            case "DESCENT_MACH" -> builder.descentMach(Double.parseDouble(value));
            case "IDLE_DESCENT_RATE" -> builder.idleDescentRate(Double.parseDouble(value));
            case "DESCENT_RATE_AT" -> {
                if (!condition.isEmpty()) {
                    descentRates.put(condition, Double.parseDouble(value));
                }
            }
        }
    }

    public AircraftPerformance getPerformance(String aircraftType) {
        return performanceCache.get(aircraftType);
    }

    public Set<String> getAvailableAircraftTypes() {
        return performanceCache.keySet();
    }

    public void reloadBadaData() {
        performanceCache.clear();
        loadAllBadaData();
    }
}
