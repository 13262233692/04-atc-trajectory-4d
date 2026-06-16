import { Plane, MapPin, Clock, Fuel, Gauge, Thermometer, Wind, ArrowUp, ArrowDown, Navigation } from 'lucide-react';
import { format } from 'date-fns';

function FlightInfoPanel({ flight, currentPoint, trajectoryPoints }) {
  const getFlightPhaseClass = (phase) => {
    switch (phase) {
      case 'TAKEOFF':
        return 'flight-phase-takeoff';
      case 'CLIMB':
        return 'flight-phase-climb';
      case 'CRUISE':
        return 'flight-phase-cruise';
      case 'DESCENT':
        return 'flight-phase-descent';
      case 'LANDING':
        return 'flight-phase-landing';
      default:
        return 'text-gray-400';
    }
  };

  const getFlightPhaseIcon = (phase) => {
    switch (phase) {
      case 'CLIMB':
        return <ArrowUp className="w-4 h-4" />;
      case 'DESCENT':
        return <ArrowDown className="w-4 h-4" />;
      case 'CRUISE':
        return <Navigation className="w-4 h-4" />;
      default:
        return <Plane className="w-4 h-4" />;
    }
  };

  const formatTime = (timestamp) => {
    if (!timestamp) return '-';
    return format(new Date(timestamp), 'HH:mm:ss');
  };

  const formatNumber = (value, decimals = 1) => {
    if (value === undefined || value === null || isNaN(value)) return '-';
    return value.toFixed(decimals);
  };

  return (
    <div className="p-4 space-y-4">
      {/* Route Info */}
      <div className="space-y-3">
        <h3 className="data-label">Route Information</h3>
        <div className="grid grid-cols-2 gap-4">
          <div>
            <div className="data-label">Airline</div>
            <div className="data-value">{flight.airline || '-'}</div>
          </div>
          <div>
            <div className="data-label">Aircraft</div>
            <div className="data-value">{flight.aircraftType || '-'}</div>
          </div>
        </div>

        <div className="flex items-center justify-between bg-atc-panel-light/50 rounded-lg p-3">
          <div className="text-center">
            <div className="text-xl font-bold text-white">{flight.departureAirport}</div>
            <div className="text-xs text-gray-400">Departure</div>
          </div>
          <div className="flex-1 px-4">
            <div className="h-px bg-gradient-to-r from-green-500 via-yellow-500 to-red-500 relative">
              <Plane className="absolute top-1/2 left-1/2 transform -translate-x-1/2 -translate-y-1/2 w-4 h-4 text-atc-accent" />
            </div>
          </div>
          <div className="text-center">
            <div className="text-xl font-bold text-white">{flight.arrivalAirport}</div>
            <div className="text-xs text-gray-400">Arrival</div>
          </div>
        </div>

        <div className="grid grid-cols-2 gap-4">
          <div>
            <div className="data-label flex items-center gap-1">
              <Clock className="w-3 h-3" />
              Departure Time
            </div>
            <div className="data-value">{flight.departureTime ? format(new Date(flight.departureTime), 'MMM dd, HH:mm') : '-'}</div>
          </div>
          <div>
            <div className="data-label flex items-center gap-1">
              <Clock className="w-3 h-3" />
              Est. Arrival
            </div>
            <div className="data-value">{flight.estimatedArrivalTime ? format(new Date(flight.estimatedArrivalTime), 'MMM dd, HH:mm') : '-'}</div>
          </div>
        </div>
      </div>

      {/* Current Status */}
      {currentPoint && (
        <>
          <div className="border-t border-white/10 pt-4 space-y-3">
            <div className="flex items-center justify-between">
              <h3 className="data-label">Current Status</h3>
              <div className={`flex items-center gap-1 px-2 py-1 rounded-full bg-atc-panel-light ${getFlightPhaseClass(currentPoint.flightPhase)}`}>
                {getFlightPhaseIcon(currentPoint.flightPhase)}
                <span className="text-xs font-medium">{currentPoint.flightPhase}</span>
              </div>
            </div>

            <div className="grid grid-cols-2 gap-3">
              <div className="bg-atc-panel-light/50 rounded-lg p-3">
                <div className="data-label flex items-center gap-1 mb-1">
                  <MapPin className="w-3 h-3" />
                  Altitude
                </div>
                <div className="data-value text-lg">{formatNumber(currentPoint.altitude, 0)} <span className="text-xs text-gray-400">m</span></div>
              </div>
              <div className="bg-atc-panel-light/50 rounded-lg p-3">
                <div className="data-label flex items-center gap-1 mb-1">
                  <Gauge className="w-3 h-3" />
                  Ground Speed
                </div>
                <div className="data-value text-lg">{formatNumber(currentPoint.groundSpeed, 0)} <span className="text-xs text-gray-400">m/s</span></div>
              </div>
              <div className="bg-atc-panel-light/50 rounded-lg p-3">
                <div className="data-label flex items-center gap-1 mb-1">
                  <Navigation className="w-3 h-3" />
                  Heading
                </div>
                <div className="data-value text-lg">{formatNumber(currentPoint.heading, 1)} <span className="text-xs text-gray-400">°</span></div>
              </div>
              <div className="bg-atc-panel-light/50 rounded-lg p-3">
                <div className="data-label flex items-center gap-1 mb-1">
                  <Gauge className="w-3 h-3" />
                  Mach
                </div>
                <div className="data-value text-lg">{formatNumber(currentPoint.machNumber, 2)}</div>
              </div>
            </div>
          </div>

          {/* Performance */}
          <div className="border-t border-white/10 pt-4 space-y-3">
            <h3 className="data-label">Performance Data</h3>
            <div className="space-y-2">
              <div className="flex justify-between items-center">
                <span className="text-sm text-gray-400">True Airspeed</span>
                <span className="text-sm text-white font-mono">{formatNumber(currentPoint.trueAirspeed, 1)} m/s</span>
              </div>
              <div className="flex justify-between items-center">
                <span className="text-sm text-gray-400">Vertical Speed</span>
                <span className="text-sm text-white font-mono">{formatNumber(currentPoint.verticalSpeed, 1)} m/s</span>
              </div>
              <div className="flex justify-between items-center">
                <span className="text-sm text-gray-400">Track Angle</span>
                <span className="text-sm text-white font-mono">{formatNumber(currentPoint.trackAngle, 1)}°</span>
              </div>
              <div className="flex justify-between items-center">
                <span className="text-sm text-gray-400">Aircraft Mass</span>
                <span className="text-sm text-white font-mono">{formatNumber(currentPoint.mass, 0)} kg</span>
              </div>
              <div className="flex justify-between items-center">
                <span className="text-sm text-gray-400">Fuel Mass</span>
                <span className="text-sm text-white font-mono">{formatNumber(currentPoint.fuelMass, 0)} kg</span>
              </div>
            </div>
          </div>

          {/* Forces */}
          <div className="border-t border-white/10 pt-4 space-y-3">
            <h3 className="data-label">Aerodynamic Forces</h3>
            <div className="space-y-2">
              <div className="flex justify-between items-center">
                <span className="text-sm text-gray-400">Thrust</span>
                <span className="text-sm text-white font-mono">{formatNumber(currentPoint.thrust, 0)} N</span>
              </div>
              <div className="flex justify-between items-center">
                <span className="text-sm text-gray-400">Drag</span>
                <span className="text-sm text-white font-mono">{formatNumber(currentPoint.drag, 0)} N</span>
              </div>
              <div className="flex justify-between items-center">
                <span className="text-sm text-gray-400">Lift</span>
                <span className="text-sm text-white font-mono">{formatNumber(currentPoint.lift, 0)} N</span>
              </div>
              <div className="flex justify-between items-center">
                <span className="text-sm text-gray-400">Fuel Flow</span>
                <span className="text-sm text-white font-mono">{formatNumber(currentPoint.fuelFlow, 2)} kg/s</span>
              </div>
            </div>
          </div>

          {/* Weather */}
          <div className="border-t border-white/10 pt-4 space-y-3">
            <h3 className="data-label">Weather Conditions</h3>
            <div className="space-y-2">
              <div className="flex justify-between items-center">
                <span className="text-sm text-gray-400 flex items-center gap-1">
                  <Wind className="w-3 h-3" />
                  Wind Speed
                </span>
                <span className="text-sm text-white font-mono">{formatNumber(currentPoint.windSpeed, 1)} m/s</span>
              </div>
              <div className="flex justify-between items-center">
                <span className="text-sm text-gray-400 flex items-center gap-1">
                  <Navigation className="w-3 h-3" />
                  Wind Direction
                </span>
                <span className="text-sm text-white font-mono">{formatNumber(currentPoint.windDirection, 0)}°</span>
              </div>
              <div className="flex justify-between items-center">
                <span className="text-sm text-gray-400 flex items-center gap-1">
                  <Thermometer className="w-3 h-3" />
                  Temperature
                </span>
                <span className="text-sm text-white font-mono">{formatNumber(currentPoint.temperature, 1)} °C</span>
              </div>
            </div>
          </div>

          {/* Progress */}
          <div className="border-t border-white/10 pt-4 space-y-3">
            <h3 className="data-label">Flight Progress</h3>
            <div className="space-y-2">
              <div className="flex justify-between items-center">
                <span className="text-sm text-gray-400">Next Waypoint</span>
                <span className="text-sm text-white font-mono">{currentPoint.nextWaypoint || '-'}</span>
              </div>
              <div className="flex justify-between items-center">
                <span className="text-sm text-gray-400">Distance to Dest.</span>
                <span className="text-sm text-white font-mono">{formatNumber(currentPoint.distanceToDestination, 0)} m</span>
              </div>
              <div className="flex justify-between items-center">
                <span className="text-sm text-gray-400">Time to Dest.</span>
                <span className="text-sm text-white font-mono">{formatNumber(currentPoint.timeToDestination, 0)} s</span>
              </div>
              <div className="flex justify-between items-center">
                <span className="text-sm text-gray-400">Specific Range</span>
                <span className="text-sm text-white font-mono">{formatNumber(currentPoint.specificRange, 4)} m/kg</span>
              </div>
            </div>
          </div>

          {/* Timestamp */}
          <div className="border-t border-white/10 pt-4">
            <div className="flex justify-between items-center">
              <span className="text-xs text-gray-400">Last Update</span>
              <span className="text-xs text-white font-mono">{formatTime(currentPoint.timestamp)}</span>
            </div>
          </div>
        </>
      )}

      {/* Waypoints */}
      {flight.waypoints && flight.waypoints.length > 0 && (
        <div className="border-t border-white/10 pt-4 space-y-3">
          <h3 className="data-label">Route Waypoints ({flight.waypoints.length})</h3>
          <div className="max-h-40 overflow-y-auto scrollbar-thin space-y-1">
            {flight.waypoints.map((waypoint, index) => (
              <div
                key={index}
                className="flex items-center gap-2 text-sm py-1 px-2 rounded hover:bg-atc-panel-light/50"
              >
                <div className="w-5 h-5 rounded-full bg-atc-panel-light flex items-center justify-center text-xs text-gray-400">
                  {index + 1}
                </div>
                <span className="font-mono text-white flex-1">{waypoint.name}</span>
                <span className="text-xs text-gray-400">
                  {waypoint.altitude ? `${waypoint.altitude.toFixed(0)}m` : '-'}
                </span>
              </div>
            ))}
          </div>
        </div>
      )}
    </div>
  );
}

export default FlightInfoPanel;
