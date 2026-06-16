package com.atc.trajectory4d.bada.model;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.util.Map;

@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class AircraftPerformance {

    private String aircraftType;
    private String engineType;
    private int numberOfEngines;

    private double referenceMass;
    private double maxTakeoffMass;
    private double maxLandingMass;
    private double maxZeroFuelMass;
    private double operatingEmptyMass;

    private double wingArea;
    private double wingSpan;
    private double wingAspectRatio;

    private double maxOperatingMach;
    private double maxOperatingSpeed;
    private double cruiseMach;
    private double cruiseSpeed;

    private double serviceCeiling;
    private double maxAltitude;

    private double stallSpeedLanding;
    private double stallSpeedTakeoff;

    private DragPolar dragPolar;
    private ThrustModel thrustModel;
    private FuelFlowModel fuelFlowModel;

    private ClimbPerformance climbPerformance;
    private CruisePerformance cruisePerformance;
    private DescentPerformance descentPerformance;

    @Data
    @Builder
    @NoArgsConstructor
    @AllArgsConstructor
    public static class DragPolar {
        private double cd0;
        private double cd2;
        private double cd4;
        private double k;
        private double machDragIndex;
    }

    @Data
    @Builder
    @NoArgsConstructor
    @AllArgsConstructor
    public static class ThrustModel {
        private double maxTakeoffThrust;
        private double maxClimbThrust;
        private double maxCruiseThrust;
        private double thrustAltitudeFactor;
        private double thrustMachFactor;
        private double thrustTemperatureFactor;
    }

    @Data
    @Builder
    @NoArgsConstructor
    @AllArgsConstructor
    public static class FuelFlowModel {
        private double cf1;
        private double cf2;
        private double cr1;
        private double cr2;
        private double cd1;
        private double cd2;
        private double idleFuelFlow;
        private double fuelDensity;
    }

    @Data
    @Builder
    @NoArgsConstructor
    @AllArgsConstructor
    public static class ClimbPerformance {
        private double initialClimbRate;
        private double climbSpeed;
        private double climbMach;
        private double transitionAltitude;
        private Map<String, Double> climbRatesByAltitude;
    }

    @Data
    @Builder
    @NoArgsConstructor
    @AllArgsConstructor
    public static class CruisePerformance {
        private double optimumCruiseAltitude;
        private double maximumCruiseAltitude;
        private double fuelFlowAtCruise;
        private Map<String, Double> fuelFlowByAltitude;
    }

    @Data
    @Builder
    @NoArgsConstructor
    @AllArgsConstructor
    public static class DescentPerformance {
        private double descentRate;
        private double descentSpeed;
        private double descentMach;
        private double idleDescentRate;
        private Map<String, Double> descentRatesByAltitude;
    }
}
