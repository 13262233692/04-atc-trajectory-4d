package com.atc.trajectory4d.trajectory;

import com.atc.trajectory4d.bada.BadaService;
import com.atc.trajectory4d.bada.model.AircraftPerformance;
import com.atc.trajectory4d.config.AppProperties;
import com.atc.trajectory4d.model.FlightPlan;
import com.atc.trajectory4d.model.TrajectoryPoint4D;
import com.atc.trajectory4d.model.Waypoint;
import com.atc.trajectory4d.weather.WeatherData;
import com.atc.trajectory4d.weather.WeatherService;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;

import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.List;

@Slf4j
@Service
@RequiredArgsConstructor
public class TrajectoryCalculator {

    private final BadaService badaService;
    private final WeatherService weatherService;
    private final RungeKuttaIntegrator integrator;
    private final AppProperties appProperties;

    private static final double EARTH_RADIUS = 6371000.0;
    private static final double RAD_TO_DEG = 180.0 / Math.PI;
    private static final double DEG_TO_RAD = Math.PI / 180.0;

    public List<TrajectoryPoint4D> calculate4DTrajectory(FlightPlan flightPlan) {
        log.info("Starting 4D trajectory calculation for flight: {}", flightPlan.getFlightId());

        List<TrajectoryPoint4D> trajectory = new ArrayList<>();
        AircraftPerformance performance = badaService.getAircraftPerformance(flightPlan.getAircraftType());

        if (flightPlan.getWaypoints() == null || flightPlan.getWaypoints().size() < 2) {
            log.error("Flight plan must have at least 2 waypoints");
            return trajectory;
        }

        FlightStateVector currentState = initializeState(flightPlan, performance);
        LocalDateTime currentTime = flightPlan.getDepartureTime();
        double timeStep = appProperties.getTrajectory().getTimeStep();
        double currentTimeSeconds = 0.0;
        long sequence = 0;
        int waypointIndex = 1;

        Waypoint currentWaypoint = flightPlan.getWaypoints().get(0);
        Waypoint nextWaypoint = flightPlan.getWaypoints().get(waypointIndex);

        int maxIterations = appProperties.getTrajectory().getMaxIteration();
        int iterations = 0;

        while (iterations < maxIterations) {
            iterations++;

            BadaService.FlightPhase phase = determineFlightPhase(
                    currentState.getAltitude(),
                    flightPlan.getCruiseAltitude(),
                    performance.getServiceCeiling(),
                    currentWaypoint,
                    nextWaypoint
            );

            double distanceToNext = calculateDistanceToNext(currentState, nextWaypoint);

            if (distanceToNext < 5000 && waypointIndex < flightPlan.getWaypoints().size() - 1) {
                waypointIndex++;
                currentWaypoint = nextWaypoint;
                nextWaypoint = flightPlan.getWaypoints().get(waypointIndex);
                log.debug("Passed waypoint {}, next: {}", currentWaypoint.getName(), nextWaypoint.getName());
            }

            TrajectoryPoint4D point = createTrajectoryPoint(
                    currentState,
                    currentTime,
                    sequence++,
                    flightPlan,
                    performance,
                    phase,
                    nextWaypoint
            );
            trajectory.add(point);

            if (hasReachedDestination(currentState, flightPlan)) {
                log.info("Trajectory calculation completed for flight {} after {} points",
                        flightPlan.getFlightId(), trajectory.size());
                break;
            }

            double bearingToNext = calculateBearingToNext(currentState, nextWaypoint);

            FlightStateVector derivative = calculateStateDerivative(
                    currentTimeSeconds,
                    currentState,
                    performance,
                    phase,
                    bearingToNext,
                    flightPlan,
                    nextWaypoint
            );

            currentState = integrator.integrate(
                    currentState,
                    currentTimeSeconds,
                    timeStep,
                    (t, s) -> calculateStateDerivative(
                            t, s, performance, phase, bearingToNext, flightPlan, nextWaypoint
                    )
            );

            currentTime = currentTime.plusSeconds((long) timeStep);
            currentTimeSeconds += timeStep;

            currentState.setAltitude(Math.max(0.0, currentState.getAltitude()));
            currentState.setTrueAirspeed(Math.max(50.0, currentState.getTrueAirspeed()));
            currentState.setMass(Math.max(performance.getOperatingEmptyMass(), currentState.getMass()));
            currentState.setFuelMass(Math.max(0.0, currentState.getFuelMass()));
        }

        if (iterations >= maxIterations) {
            log.warn("Trajectory calculation reached maximum iterations for flight {}", flightPlan.getFlightId());
        }

        return trajectory;
    }

    private FlightStateVector initializeState(FlightPlan flightPlan, AircraftPerformance performance) {
        Waypoint departure = flightPlan.getDepartureWaypoint();

        double initialAltitude = departure.getAltitude() != null ? departure.getAltitude() : 0.0;
        double initialSpeed = departure.getSpeed() != null ? departure.getSpeed() : 80.0;

        return FlightStateVector.builder()
                .longitude(departure.getLongitude())
                .latitude(departure.getLatitude())
                .altitude(initialAltitude)
                .trueAirspeed(initialSpeed)
                .heading(departure.bearingTo(flightPlan.getWaypoints().get(1)))
                .verticalSpeed(0.0)
                .mass(flightPlan.getInitialMass())
                .fuelMass(flightPlan.getFuelMass())
                .build();
    }

    private BadaService.FlightPhase determineFlightPhase(
            double altitude,
            double cruiseAltitude,
            double serviceCeiling,
            Waypoint current,
            Waypoint next) {

        if (current.isDeparture() && altitude < 1000) {
            return BadaService.FlightPhase.TAKEOFF;
        }

        if (next.isDestination() && altitude < 3000) {
            return BadaService.FlightPhase.LANDING;
        }

        if (altitude < cruiseAltitude - 300 && next.getAltitude() == null ||
                (next.getAltitude() != null && next.getAltitude() > altitude + 300)) {
            return BadaService.FlightPhase.CLIMB;
        }

        if (altitude > cruiseAltitude - 300 && altitude < cruiseAltitude + 300) {
            return BadaService.FlightPhase.CRUISE;
        }

        if (next.getAltitude() != null && next.getAltitude() < altitude - 300) {
            return BadaService.FlightPhase.DESCENT;
        }

        return BadaService.FlightPhase.CRUISE;
    }

    private FlightStateVector calculateStateDerivative(
            double time,
            FlightStateVector state,
            AircraftPerformance performance,
            BadaService.FlightPhase phase,
            double targetBearing,
            FlightPlan flightPlan,
            Waypoint nextWaypoint) {

        WeatherData weather = weatherService.getWeatherAt(
                state.getLatitude(), state.getLongitude(), state.getAltitude()
        );

        double mach = badaService.calculateMachNumber(state.getTrueAirspeed(), state.getAltitude());

        double thrust = badaService.calculateThrust(performance, state.getAltitude(), mach, phase);

        double liftCoefficient = 2.0 * state.getMass() * 9.81 /
                (weather.getDensity() * state.getTrueAirspeed() * state.getTrueAirspeed() * performance.getWingArea());
        double drag = badaService.calculateDrag(performance, state.getAltitude(), state.getTrueAirspeed(), liftCoefficient);

        double acceleration = (thrust - drag) / state.getMass();

        double targetVerticalSpeed = calculateTargetVerticalSpeed(
                state, performance, phase, flightPlan, nextWaypoint
        );
        double verticalAcceleration = (targetVerticalSpeed - state.getVerticalSpeed()) / 5.0;

        double fuelFlow = badaService.calculateFuelFlow(
                performance, state.getAltitude(), thrust, mach
        );

        double groundSpeed = calculateGroundSpeed(state, weather);
        double headingRate = calculateHeadingRate(state, targetBearing);

        double dLon_dt = (groundSpeed * Math.cos(Math.toRadians(state.getHeading())) + weather.getWindComponentU(state.getAltitude()))
                / (EARTH_RADIUS * Math.cos(Math.toRadians(state.getLatitude()))) * RAD_TO_DEG;

        double dLat_dt = (groundSpeed * Math.sin(Math.toRadians(state.getHeading())) + weather.getWindComponentV(state.getAltitude()))
                / EARTH_RADIUS * RAD_TO_DEG;

        double dTas_dt = acceleration;

        return FlightStateVector.builder()
                .longitude(dLon_dt)
                .latitude(dLat_dt)
                .altitude(state.getVerticalSpeed())
                .trueAirspeed(dTas_dt)
                .heading(headingRate)
                .verticalSpeed(verticalAcceleration)
                .mass(-fuelFlow)
                .fuelMass(-fuelFlow)
                .build();
    }

    private double calculateTargetVerticalSpeed(
            FlightStateVector state,
            AircraftPerformance performance,
            BadaService.FlightPhase phase,
            FlightPlan flightPlan,
            Waypoint nextWaypoint) {

        double mach = badaService.calculateMachNumber(state.getTrueAirspeed(), state.getAltitude());

        return switch (phase) {
            case TAKEOFF -> 8.0;
            case CLIMB -> badaService.calculateClimbRate(
                    performance, state.getAltitude(), state.getMass(), mach);
            case CRUISE -> 0.0;
            case DESCENT -> -badaService.calculateDescentRate(
                    performance, state.getAltitude(), state.getMass(), mach);
            case LANDING -> -6.0;
        };
    }

    private double calculateGroundSpeed(FlightStateVector state, WeatherData weather) {
        double windU = weather.getWindComponentU(state.getAltitude());
        double windV = weather.getWindComponentV(state.getAltitude());
        double headingRad = Math.toRadians(state.getHeading());

        double airspeedX = state.getTrueAirspeed() * Math.cos(headingRad);
        double airspeedY = state.getTrueAirspeed() * Math.sin(headingRad);

        double groundX = airspeedX + windU;
        double groundY = airspeedY + windV;

        return Math.sqrt(groundX * groundX + groundY * groundY);
    }

    private double calculateHeadingRate(FlightStateVector state, double targetBearing) {
        double headingError = targetBearing - state.getHeading();
        if (headingError > 180) headingError -= 360;
        if (headingError < -180) headingError += 360;

        double maxTurnRate = 3.0;
        return Math.max(-maxTurnRate, Math.min(maxTurnRate, headingError * 0.5));
    }

    private double calculateBearingToNext(FlightStateVector state, Waypoint nextWaypoint) {
        return Waypoint.calculateBearing(
                state.getLatitude(), state.getLongitude(),
                nextWaypoint.getLatitude(), nextWaypoint.getLongitude()
        );
    }

    private double calculateDistanceToNext(FlightStateVector state, Waypoint nextWaypoint) {
        return Waypoint.calculateGreatCircleDistance(
                state.getLatitude(), state.getLongitude(),
                nextWaypoint.getLatitude(), nextWaypoint.getLongitude()
        );
    }

    private boolean hasReachedDestination(FlightStateVector state, FlightPlan flightPlan) {
        Waypoint destination = flightPlan.getArrivalWaypoint();
        if (destination == null) return false;

        double distance = Waypoint.calculateGreatCircleDistance(
                state.getLatitude(), state.getLongitude(),
                destination.getLatitude(), destination.getLongitude()
        );

        return distance < 3000 && state.getAltitude() < 500;
    }

    private TrajectoryPoint4D createTrajectoryPoint(
            FlightStateVector state,
            LocalDateTime time,
            long sequence,
            FlightPlan flightPlan,
            AircraftPerformance performance,
            BadaService.FlightPhase phase,
            Waypoint nextWaypoint) {

        WeatherData weather = weatherService.getWeatherAt(
                state.getLatitude(), state.getLongitude(), state.getAltitude()
        );

        double mach = badaService.calculateMachNumber(state.getTrueAirspeed(), state.getAltitude());
        double groundSpeed = calculateGroundSpeed(state, weather);
        double trackAngle = calculateTrackAngle(state, weather);
        double thrust = badaService.calculateThrust(performance, state.getAltitude(), mach, phase);
        double liftCoefficient = 2.0 * state.getMass() * 9.81 /
                (weather.getDensity() * state.getTrueAirspeed() * state.getTrueAirspeed() * performance.getWingArea());
        double drag = badaService.calculateDrag(performance, state.getAltitude(), state.getTrueAirspeed(), liftCoefficient);
        double lift = liftCoefficient * 0.5 * weather.getDensity() * state.getTrueAirspeed() * state.getTrueAirspeed() * performance.getWingArea();
        double fuelFlow = badaService.calculateFuelFlow(performance, state.getAltitude(), thrust, mach);
        double specificRange = groundSpeed / Math.max(0.1, fuelFlow);

        Waypoint destination = flightPlan.getArrivalWaypoint();
        double distanceToDest = 0.0;
        if (destination != null) {
            distanceToDest = Waypoint.calculateGreatCircleDistance(
                    state.getLatitude(), state.getLongitude(),
                    destination.getLatitude(), destination.getLongitude()
            );
        }

        return TrajectoryPoint4D.builder()
                .flightId(flightPlan.getFlightId())
                .sequenceNumber(sequence)
                .longitude(state.getLongitude())
                .latitude(state.getLatitude())
                .altitude(state.getAltitude())
                .timestamp(time)
                .trueAirspeed(state.getTrueAirspeed())
                .groundSpeed(groundSpeed)
                .machNumber(mach)
                .heading(state.getHeading())
                .trackAngle(trackAngle)
                .verticalSpeed(state.getVerticalSpeed())
                .mass(state.getMass())
                .fuelMass(state.getFuelMass())
                .thrust(thrust)
                .drag(drag)
                .lift(lift)
                .fuelFlow(fuelFlow)
                .specificRange(specificRange)
                .windSpeed(weather.getWindSpeedAtAltitude(state.getAltitude()))
                .windDirection(weather.getWindDirection())
                .temperature(weather.getTemperatureAtAltitude(state.getAltitude()))
                .flightPhase(phase.name())
                .nextWaypoint(nextWaypoint.getName())
                .distanceToDestination(distanceToDest)
                .timeToDestination(distanceToDest / Math.max(1.0, groundSpeed))
                .build();
    }

    private double calculateTrackAngle(FlightStateVector state, WeatherData weather) {
        double windU = weather.getWindComponentU(state.getAltitude());
        double windV = weather.getWindComponentV(state.getAltitude());
        double headingRad = Math.toRadians(state.getHeading());

        double airspeedX = state.getTrueAirspeed() * Math.cos(headingRad);
        double airspeedY = state.getTrueAirspeed() * Math.sin(headingRad);

        double groundX = airspeedX + windU;
        double groundY = airspeedY + windV;

        return (Math.toDegrees(Math.atan2(groundY, groundX)) + 360) % 360;
    }
}
