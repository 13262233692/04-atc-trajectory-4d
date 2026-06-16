package com.atc.trajectory4d.controller;

import com.atc.trajectory4d.bada.BadaService;
import com.atc.trajectory4d.bada.model.AircraftPerformance;
import com.atc.trajectory4d.model.dto.ApiResponse;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import java.util.HashMap;
import java.util.Map;
import java.util.Set;

@Slf4j
@RestController
@RequestMapping("/bada")
@RequiredArgsConstructor
public class BadaController {

    private final BadaService badaService;

    @GetMapping("/aircraft")
    public ResponseEntity<ApiResponse<Set<String>>> getAvailableAircraftTypes() {
        Set<String> types = badaService.getAvailableAircraftTypes();
        return ResponseEntity.ok(ApiResponse.success(types));
    }

    @GetMapping("/aircraft/{aircraftType}")
    public ResponseEntity<ApiResponse<AircraftPerformance>> getAircraftPerformance(
            @PathVariable String aircraftType) {

        AircraftPerformance performance = badaService.getAircraftPerformance(aircraftType);
        if (performance == null) {
            return ResponseEntity.notFound().build();
        }

        return ResponseEntity.ok(ApiResponse.success(performance));
    }

    @GetMapping("/aircraft/{aircraftType}/climb-rate")
    public ResponseEntity<ApiResponse<Map<String, Object>>> calculateClimbRate(
            @PathVariable String aircraftType,
            @RequestParam double altitude,
            @RequestParam double mass,
            @RequestParam double mach) {

        AircraftPerformance performance = badaService.getAircraftPerformance(aircraftType);
        if (performance == null) {
            return ResponseEntity.notFound().build();
        }

        double climbRate = badaService.calculateClimbRate(performance, altitude, mass, mach);

        Map<String, Object> result = new HashMap<>();
        result.put("aircraftType", aircraftType);
        result.put("altitude", altitude);
        result.put("mass", mass);
        result.put("mach", mach);
        result.put("climbRate", climbRate);
        result.put("climbRateFpm", climbRate * 196.85);

        return ResponseEntity.ok(ApiResponse.success(result));
    }

    @GetMapping("/aircraft/{aircraftType}/fuel-flow")
    public ResponseEntity<ApiResponse<Map<String, Object>>> calculateFuelFlow(
            @PathVariable String aircraftType,
            @RequestParam double altitude,
            @RequestParam double thrust,
            @RequestParam double mach) {

        AircraftPerformance performance = badaService.getAircraftPerformance(aircraftType);
        if (performance == null) {
            return ResponseEntity.notFound().build();
        }

        double fuelFlow = badaService.calculateFuelFlow(performance, altitude, thrust, mach);

        Map<String, Object> result = new HashMap<>();
        result.put("aircraftType", aircraftType);
        result.put("altitude", altitude);
        result.put("thrust", thrust);
        result.put("mach", mach);
        result.put("fuelFlow", fuelFlow);
        result.put("fuelFlowKgPerHour", fuelFlow * 3600.0);

        return ResponseEntity.ok(ApiResponse.success(result));
    }

    @PostMapping("/reload")
    public ResponseEntity<ApiResponse<Map<String, Object>>> reloadBadaData() {
        badaService.reloadBadaData();

        Map<String, Object> result = new HashMap<>();
        result.put("loadedAircraftCount", badaService.getAvailableAircraftTypes().size());
        result.put("aircraftTypes", badaService.getAvailableAircraftTypes());

        return ResponseEntity.ok(ApiResponse.success("BADA data reloaded", result));
    }
}
