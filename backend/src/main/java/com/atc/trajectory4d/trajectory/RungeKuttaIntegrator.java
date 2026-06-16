package com.atc.trajectory4d.trajectory;

import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Component;

import java.util.function.BiFunction;

@Slf4j
@Component
public class RungeKuttaIntegrator {

    public interface StateDerivativeFunction {
        FlightStateVector compute(double time, FlightStateVector state);
    }

    public FlightStateVector integrate(
            FlightStateVector initialState,
            double initialTime,
            double timeStep,
            StateDerivativeFunction derivativeFunction) {

        FlightStateVector k1 = derivativeFunction.compute(initialTime, initialState);

        FlightStateVector k2State = initialState.add(k1.scale(timeStep / 2.0));
        FlightStateVector k2 = derivativeFunction.compute(initialTime + timeStep / 2.0, k2State);

        FlightStateVector k3State = initialState.add(k2.scale(timeStep / 2.0));
        FlightStateVector k3 = derivativeFunction.compute(initialTime + timeStep / 2.0, k3State);

        FlightStateVector k4State = initialState.add(k3.scale(timeStep));
        FlightStateVector k4 = derivativeFunction.compute(initialTime + timeStep, k4State);

        FlightStateVector weightedSum = k1.scale(1.0)
                .add(k2.scale(2.0))
                .add(k3.scale(2.0))
                .add(k4.scale(1.0));

        return initialState.add(weightedSum.scale(timeStep / 6.0));
    }

    public double[] integrate(
            double[] y,
            double t,
            double dt,
            BiFunction<Double, double[], double[]> function) {

        double[] k1 = function.apply(t, y);

        double[] y2 = add(y, scale(k1, dt / 2.0));
        double[] k2 = function.apply(t + dt / 2.0, y2);

        double[] y3 = add(y, scale(k2, dt / 2.0));
        double[] k3 = function.apply(t + dt / 2.0, y3);

        double[] y4 = add(y, scale(k3, dt));
        double[] k4 = function.apply(t + dt, y4);

        double[] result = new double[y.length];
        for (int i = 0; i < y.length; i++) {
            result[i] = y[i] + (dt / 6.0) * (k1[i] + 2 * k2[i] + 2 * k3[i] + k4[i]);
        }

        return result;
    }

    private double[] add(double[] a, double[] b) {
        double[] result = new double[a.length];
        for (int i = 0; i < a.length; i++) {
            result[i] = a[i] + b[i];
        }
        return result;
    }

    private double[] scale(double[] a, double factor) {
        double[] result = new double[a.length];
        for (int i = 0; i < a.length; i++) {
            result[i] = a[i] * factor;
        }
        return result;
    }

    public double adaptiveIntegrate(
            FlightStateVector initialState,
            double initialTime,
            double maxTimeStep,
            double tolerance,
            StateDerivativeFunction derivativeFunction) {

        double timeStep = maxTimeStep;
        double currentTime = initialTime;

        FlightStateVector state1 = integrate(initialState, currentTime, timeStep, derivativeFunction);
        FlightStateVector state2HalfStep1 = integrate(initialState, currentTime, timeStep / 2.0, derivativeFunction);
        FlightStateVector state2 = integrate(state2HalfStep1, currentTime + timeStep / 2.0, timeStep / 2.0, derivativeFunction);

        double error = calculateError(state1, state2);

        while (error > tolerance && timeStep > 0.1) {
            timeStep /= 2.0;
            state1 = integrate(initialState, currentTime, timeStep, derivativeFunction);
            state2HalfStep1 = integrate(initialState, currentTime, timeStep / 2.0, derivativeFunction);
            state2 = integrate(state2HalfStep1, currentTime + timeStep / 2.0, timeStep / 2.0, derivativeFunction);
            error = calculateError(state1, state2);
        }

        return timeStep;
    }

    private double calculateError(FlightStateVector state1, FlightStateVector state2) {
        double error = 0.0;
        error += Math.abs(state1.getLongitude() - state2.getLongitude());
        error += Math.abs(state1.getLatitude() - state2.getLatitude());
        error += Math.abs(state1.getAltitude() - state2.getAltitude()) / 1000.0;
        error += Math.abs(state1.getTrueAirspeed() - state2.getTrueAirspeed()) / 100.0;
        error += Math.abs(state1.getHeading() - state2.getHeading()) / 180.0;
        error += Math.abs(state1.getVerticalSpeed() - state2.getVerticalSpeed()) / 10.0;
        return error;
    }
}
