package com.atc.trajectory4d.bada;

import com.atc.trajectory4d.bada.model.AircraftPerformance;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;

import java.util.Set;

@Slf4j
@Service
@RequiredArgsConstructor
public class BadaService {

    private final BadaFileParser badaFileParser;

    public AircraftPerformance getAircraftPerformance(String aircraftType) {
        AircraftPerformance performance = badaFileParser.getPerformance(aircraftType);
        if (performance == null) {
            log.warn("Aircraft performance not found for type: {}", aircraftType);
            performance = getDefaultPerformance();
        }
        return performance;
    }

    public Set<String> getAvailableAircraftTypes() {
        return badaFileParser.getAvailableAircraftTypes();
    }

    public void reloadBadaData() {
        badaFileParser.reloadBadaData();
    }

    public double calculateClimbRate(AircraftPerformance performance, double altitude, double mass, double mach) {
        AircraftPerformance.ClimbPerformance climb = performance.getClimbPerformance();
        double baseClimbRate = climb.getInitialClimbRate();

        double altitudeFactor = 1.0 - (altitude / performance.getMaxAltitude()) * 0.7;
        double massFactor = 1.0 - ((mass - performance.getReferenceMass()) / performance.getMaxTakeoffMass()) * 0.3;
        double machFactor = 1.0 - Math.abs(mach - climb.getClimbMach()) * 0.5;

        return baseClimbRate * Math.max(0.1, altitudeFactor) * Math.max(0.1, massFactor) * Math.max(0.1, machFactor);
    }

    public double calculateDescentRate(AircraftPerformance performance, double altitude, double mass, double mach) {
        AircraftPerformance.DescentPerformance descent = performance.getDescentPerformance();
        double baseDescentRate = descent.getDescentRate();

        double altitudeFactor = 0.5 + (altitude / performance.getMaxAltitude()) * 0.5;
        double massFactor = 0.8 + ((mass - performance.getReferenceMass()) / performance.getMaxTakeoffMass()) * 0.4;

        return baseDescentRate * altitudeFactor * massFactor;
    }

    public double calculateFuelFlow(AircraftPerformance performance, double altitude, double thrust, double mach) {
        AircraftPerformance.FuelFlowModel fuelFlow = performance.getFuelFlowModel();

        double altitudeFactor = Math.exp(-altitude / 10000.0);
        double thrustFactor = thrust / performance.getThrustModel().getMaxCruiseThrust();
        double machFactor = 1.0 + mach * 0.3;

        double baseFuelFlow = fuelFlow.getCr1() * thrustFactor + fuelFlow.getCr2();
        return baseFuelFlow * altitudeFactor * machFactor;
    }

    public double calculateDrag(AircraftPerformance performance, double altitude, double velocity, double liftCoefficient) {
        AircraftPerformance.DragPolar dragPolar = performance.getDragPolar();

        double cd = dragPolar.getCd0() + dragPolar.getK() * liftCoefficient * liftCoefficient;

        double rho = calculateAirDensity(altitude);
        double dynamicPressure = 0.5 * rho * velocity * velocity;

        return cd * dynamicPressure * performance.getWingArea();
    }

    public double calculateLift(AircraftPerformance performance, double altitude, double velocity, double angleOfAttack) {
        double cl = 2.0 * Math.PI * angleOfAttack / 180.0 * Math.PI;
        double rho = calculateAirDensity(altitude);
        double dynamicPressure = 0.5 * rho * velocity * velocity;

        return cl * dynamicPressure * performance.getWingArea();
    }

    public double calculateThrust(AircraftPerformance performance, double altitude, double mach, FlightPhase phase) {
        AircraftPerformance.ThrustModel thrustModel = performance.getThrustModel();

        double baseThrust = switch (phase) {
            case TAKEOFF -> thrustModel.getMaxTakeoffThrust();
            case CLIMB -> thrustModel.getMaxClimbThrust();
            case CRUISE -> thrustModel.getMaxCruiseThrust();
            case DESCENT -> thrustModel.getMaxCruiseThrust() * 0.3;
            case LANDING -> 0.0;
        };

        double altitudeFactor = Math.exp(-altitude * thrustModel.getThrustAltitudeFactor());
        double machFactor = 1.0 + mach * thrustModel.getThrustMachFactor();

        return baseThrust * altitudeFactor * machFactor;
    }

    private double calculateAirDensity(double altitude) {
        double seaLevelDensity = 1.225;
        double scaleHeight = 8500.0;
        return seaLevelDensity * Math.exp(-altitude / scaleHeight);
    }

    public double calculateSpeedOfSound(double altitude) {
        double temperature = calculateTemperature(altitude);
        return Math.sqrt(1.4 * 287.0 * temperature);
    }

    public double calculateTemperature(double altitude) {
        double seaLevelTemp = 288.15;
        double lapseRate = 0.0065;
        return Math.max(216.65, seaLevelTemp - lapseRate * altitude);
    }

    public double calculateMachNumber(double trueAirspeed, double altitude) {
        return trueAirspeed / calculateSpeedOfSound(altitude);
    }

    public double calculateTrueAirspeed(double indicatedAirspeed, double altitude) {
        double seaLevelDensity = 1.225;
        double currentDensity = calculateAirDensity(altitude);
        return indicatedAirspeed * Math.sqrt(seaLevelDensity / currentDensity);
    }

    public AircraftPerformance getDefaultPerformance() {
        return AircraftPerformance.builder()
                .aircraftType("B737-800")
                .engineType("CFM56-7B")
                .numberOfEngines(2)
                .referenceMass(65000.0)
                .maxTakeoffMass(79000.0)
                .maxLandingMass(66000.0)
                .maxZeroFuelMass(62000.0)
                .operatingEmptyMass(41000.0)
                .wingArea(124.6)
                .wingSpan(35.8)
                .wingAspectRatio(9.44)
                .maxOperatingMach(0.82)
                .maxOperatingSpeed(470.0)
                .cruiseMach(0.78)
                .cruiseSpeed(450.0)
                .serviceCeiling(12500.0)
                .maxAltitude(13000.0)
                .stallSpeedLanding(70.0)
                .stallSpeedTakeoff(80.0)
                .dragPolar(AircraftPerformance.DragPolar.builder()
                        .cd0(0.020)
                        .cd2(0.05)
                        .cd4(0.0)
                        .k(0.045)
                        .machDragIndex(0.1)
                        .build())
                .thrustModel(AircraftPerformance.ThrustModel.builder()
                        .maxTakeoffThrust(120000.0)
                        .maxClimbThrust(100000.0)
                        .maxCruiseThrust(50000.0)
                        .thrustAltitudeFactor(0.00012)
                        .thrustMachFactor(0.05)
                        .thrustTemperatureFactor(0.002)
                        .build())
                .fuelFlowModel(AircraftPerformance.FuelFlowModel.builder()
                        .cf1(0.65)
                        .cf2(0.08)
                        .cr1(0.7)
                        .cr2(0.06)
                        .cd1(0.5)
                        .cd2(0.05)
                        .idleFuelFlow(0.1)
                        .fuelDensity(804.0)
                        .build())
                .climbPerformance(AircraftPerformance.ClimbPerformance.builder()
                        .initialClimbRate(15.0)
                        .climbSpeed(140.0)
                        .climbMach(0.78)
                        .transitionAltitude(10000.0)
                        .build())
                .cruisePerformance(AircraftPerformance.CruisePerformance.builder()
                        .optimumCruiseAltitude(11000.0)
                        .maximumCruiseAltitude(12500.0)
                        .fuelFlowAtCruise(2.5)
                        .build())
                .descentPerformance(AircraftPerformance.DescentPerformance.builder()
                        .descentRate(12.0)
                        .descentSpeed(140.0)
                        .descentMach(0.78)
                        .idleDescentRate(8.0)
                        .build())
                .build();
    }

    public enum FlightPhase {
        TAKEOFF,
        CLIMB,
        CRUISE,
        DESCENT,
        LANDING
    }
}
