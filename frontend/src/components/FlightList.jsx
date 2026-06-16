import { Plane, Clock, MapPin } from 'lucide-react';
import { format } from 'date-fns';

function FlightList({ flights, selectedFlight, onSelectFlight }) {
  const getStatusColor = (status) => {
    switch (status) {
      case 'ACTIVE':
        return 'bg-green-500';
      case 'SCHEDULED':
        return 'bg-blue-500';
      case 'COMPLETED':
        return 'bg-gray-500';
      case 'DELAYED':
        return 'bg-yellow-500';
      case 'CANCELLED':
        return 'bg-red-500';
      default:
        return 'bg-gray-500';
    }
  };

  const getStatusText = (status) => {
    switch (status) {
      case 'ACTIVE':
        return 'Active';
      case 'SCHEDULED':
        return 'Scheduled';
      case 'COMPLETED':
        return 'Completed';
      case 'DELAYED':
        return 'Delayed';
      case 'CANCELLED':
        return 'Cancelled';
      default:
        return status;
    }
  };

  return (
    <div className="flex-1 overflow-y-auto scrollbar-thin">
      {flights.length === 0 ? (
        <div className="flex flex-col items-center justify-center h-full text-gray-400 p-4">
          <Plane className="w-10 h-10 mb-2 opacity-50" />
          <p className="text-sm">No flights available</p>
        </div>
      ) : (
        <div className="p-2 space-y-2">
          {flights.map((flight) => (
            <div
              key={flight.flightId}
              onClick={() => onSelectFlight(flight)}
              className={`p-3 rounded-lg cursor-pointer transition-all duration-200 border ${
                selectedFlight?.flightId === flight.flightId
                  ? 'bg-atc-primary/30 border-atc-accent'
                  : 'bg-atc-panel-light/50 border-transparent hover:bg-atc-panel-light hover:border-white/20'
              }`}
            >
              <div className="flex items-start justify-between mb-2">
                <div className="flex items-center gap-2">
                  <div className={`w-2 h-2 rounded-full ${getStatusColor(flight.status)}`}></div>
                  <span className="text-sm font-bold text-white">{flight.flightId}</span>
                </div>
                <span className={`text-xs px-2 py-0.5 rounded-full ${getStatusColor(flight.status)} bg-opacity-20 text-white`}>
                  {getStatusText(flight.status)}
                </span>
              </div>

              <div className="flex items-center gap-2 text-xs text-gray-400 mb-2">
                <Plane className="w-3 h-3" />
                <span>{flight.aircraftType}</span>
                {flight.airline && (
                  <>
                    <span className="text-gray-600">•</span>
                    <span>{flight.airline}</span>
                  </>
                )}
              </div>

              <div className="flex items-center gap-2 text-sm">
                <div className="flex-1">
                  <div className="text-white font-medium">{flight.departureAirport}</div>
                  <div className="text-xs text-gray-500">Departure</div>
                </div>
                <div className="flex items-center gap-1 text-gray-500">
                  <MapPin className="w-3 h-3" />
                  <span className="text-xs">→</span>
                </div>
                <div className="flex-1 text-right">
                  <div className="text-white font-medium">{flight.arrivalAirport}</div>
                  <div className="text-xs text-gray-500">Arrival</div>
                </div>
              </div>

              {flight.departureTime && (
                <div className="mt-2 pt-2 border-t border-white/10 flex items-center gap-2 text-xs text-gray-400">
                  <Clock className="w-3 h-3" />
                  <span>{format(new Date(flight.departureTime), 'MMM dd, HH:mm')}</span>
                </div>
              )}
            </div>
          ))}
        </div>
      )}
    </div>
  );
}

export default FlightList;
