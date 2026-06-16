import { useState, useEffect, useCallback } from 'react';
import { Plane, Activity, MapPin, Clock, Settings, X, Play, RefreshCw } from 'lucide-react';
import CesiumGlobe from './components/CesiumGlobe';
import FlightList from './components/FlightList';
import FlightInfoPanel from './components/FlightInfoPanel';
import TrajectoryChart from './components/TrajectoryChart';
import NotificationToast from './components/NotificationToast';
import { fetchAllFlights, calculateTrajectory, startStreaming, stopStreaming } from './services/api';
import { useWebSocket } from './services/websocket';

function App() {
  const [flights, setFlights] = useState([]);
  const [selectedFlight, setSelectedFlight] = useState(null);
  const [trajectoryPoints, setTrajectoryPoints] = useState([]);
  const [currentPoint, setCurrentPoint] = useState(null);
  const [isStreaming, setIsStreaming] = useState(false);
  const [isCalculating, setIsCalculating] = useState(false);
  const [notifications, setNotifications] = useState([]);
  const [showSettings, setShowSettings] = useState(false);

  const { connect, disconnect, subscribe, isConnected } = useWebSocket();

  const addNotification = useCallback((type, message) => {
    const id = Date.now();
    setNotifications(prev => [...prev, { id, type, message }]);
    setTimeout(() => {
      setNotifications(prev => prev.filter(n => n.id !== id));
    }, 5000);
  }, []);

  useEffect(() => {
    loadFlights();
    connect();

    subscribe('/topic/notifications', (message) => {
      addNotification('info', message.body);
    });

    return () => {
      disconnect();
    };
  }, [connect, disconnect, subscribe, addNotification]);

  useEffect(() => {
    if (selectedFlight && isConnected) {
      const trajectorySub = subscribe(`/topic/trajectory/${selectedFlight.flightId}`, (message) => {
        const point = JSON.parse(message.body);
        setTrajectoryPoints(prev => [...prev, point]);
        setCurrentPoint(point);
      });

      const statusSub = subscribe(`/topic/status/${selectedFlight.flightId}`, (message) => {
        const status = JSON.parse(message.body);
        if (status === 'COMPLETED') {
          setIsStreaming(false);
          addNotification('success', `Flight ${selectedFlight.flightId} trajectory calculation completed`);
        }
      });

      return () => {
        trajectorySub.unsubscribe();
        statusSub.unsubscribe();
      };
    }
  }, [selectedFlight, isConnected, subscribe, addNotification]);

  const loadFlights = async () => {
    try {
      const data = await fetchAllFlights();
      setFlights(data);
    } catch (error) {
      addNotification('error', 'Failed to load flights');
    }
  };

  const handleSelectFlight = (flight) => {
    setSelectedFlight(flight);
    setTrajectoryPoints([]);
    setCurrentPoint(null);
  };

  const handleCalculateTrajectory = async () => {
    if (!selectedFlight) return;

    setIsCalculating(true);
    try {
      await calculateTrajectory(selectedFlight.flightId);
      addNotification('success', `Trajectory calculation started for ${selectedFlight.flightId}`);
    } catch (error) {
      addNotification('error', 'Failed to start trajectory calculation');
    } finally {
      setIsCalculating(false);
    }
  };

  const handleStartStreaming = async () => {
    if (!selectedFlight) return;

    try {
      await startStreaming(selectedFlight.flightId);
      setIsStreaming(true);
      setTrajectoryPoints([]);
      setCurrentPoint(null);
      addNotification('info', `Started streaming for ${selectedFlight.flightId}`);
    } catch (error) {
      addNotification('error', 'Failed to start streaming');
    }
  };

  const handleStopStreaming = async () => {
    if (!selectedFlight) return;

    try {
      await stopStreaming(selectedFlight.flightId);
      setIsStreaming(false);
      addNotification('info', `Stopped streaming for ${selectedFlight.flightId}`);
    } catch (error) {
      addNotification('error', 'Failed to stop streaming');
    }
  };

  const handleRefresh = () => {
    loadFlights();
    addNotification('info', 'Flight list refreshed');
  };

  const handleClearTrajectory = () => {
    setTrajectoryPoints([]);
    setCurrentPoint(null);
  };

  return (
    <div className="w-full h-full flex flex-col bg-atc-dark">
      {/* Header */}
      <header className="h-14 bg-atc-panel border-b border-white/10 flex items-center justify-between px-4 flex-shrink-0">
        <div className="flex items-center gap-3">
          <div className="w-10 h-10 bg-atc-primary rounded-lg flex items-center justify-center">
            <Plane className="w-6 h-6 text-white" />
          </div>
          <div>
            <h1 className="text-lg font-bold text-white">ATC 4D Trajectory Prediction</h1>
            <p className="text-xs text-gray-400">Real-time Flight Trajectory Monitoring System</p>
          </div>
        </div>

        <div className="flex items-center gap-4">
          <div className="flex items-center gap-2 px-3 py-1.5 bg-atc-panel-light rounded-md">
            <Activity className={`w-4 h-4 ${isConnected ? 'text-atc-success animate-pulse' : 'text-atc-danger'}`} />
            <span className="text-sm text-gray-300">
              {isConnected ? 'WebSocket Connected' : 'WebSocket Disconnected'}
            </span>
          </div>

          <button
            onClick={handleRefresh}
            className="p-2 hover:bg-atc-panel-light rounded-md transition-colors"
            title="Refresh"
          >
            <RefreshCw className="w-5 h-5 text-gray-300" />
          </button>

          <button
            onClick={() => setShowSettings(!showSettings)}
            className="p-2 hover:bg-atc-panel-light rounded-md transition-colors"
            title="Settings"
          >
            <Settings className="w-5 h-5 text-gray-300" />
          </button>
        </div>
      </header>

      {/* Main Content */}
      <div className="flex-1 flex overflow-hidden">
        {/* Left Panel - Flight List */}
        <div className="w-80 glass-panel m-3 flex flex-col overflow-hidden flex-shrink-0">
          <div className="glass-panel-header flex items-center justify-between">
            <h2 className="text-sm font-semibold text-white flex items-center gap-2">
              <Plane className="w-4 h-4" />
              Active Flights
            </h2>
            <span className="text-xs text-gray-400">{flights.length} flights</span>
          </div>

          <FlightList
            flights={flights}
            selectedFlight={selectedFlight}
            onSelectFlight={handleSelectFlight}
          />
        </div>

        {/* Center - Cesium Globe */}
        <div className="flex-1 flex flex-col relative">
          <CesiumGlobe
            trajectoryPoints={trajectoryPoints}
            currentPoint={currentPoint}
            selectedFlight={selectedFlight}
          />

          {/* Control Buttons */}
          {selectedFlight && (
            <div className="absolute top-4 left-1/2 transform -translate-x-1/2 flex gap-2 z-10">
              <button
                onClick={handleCalculateTrajectory}
                disabled={isCalculating}
                className="btn-primary flex items-center gap-2"
              >
                {isCalculating ? (
                  <RefreshCw className="w-4 h-4 animate-spin" />
                ) : (
                  <Play className="w-4 h-4" />
                )}
                Calculate Trajectory
              </button>

              {!isStreaming ? (
                <button
                  onClick={handleStartStreaming}
                  className="btn-success flex items-center gap-2"
                >
                  <Play className="w-4 h-4" />
                  Start Stream
                </button>
              ) : (
                <button
                  onClick={handleStopStreaming}
                  className="btn-danger flex items-center gap-2"
                >
                  <X className="w-4 h-4" />
                  Stop Stream
                </button>
              )}

              <button
                onClick={handleClearTrajectory}
                className="btn-secondary flex items-center gap-2"
              >
                <X className="w-4 h-4" />
                Clear
              </button>
            </div>
          )}

          {/* Stats Bar */}
          <div className="absolute bottom-4 left-4 right-4 glass-panel p-3 flex items-center justify-between z-10">
            <div className="flex items-center gap-6">
              <div className="flex items-center gap-2">
                <MapPin className="w-4 h-4 text-atc-accent" />
                <span className="data-label">Points:</span>
                <span className="data-value">{trajectoryPoints.length}</span>
              </div>

              {currentPoint && (
                <>
                  <div className="flex items-center gap-2">
                    <Clock className="w-4 h-4 text-atc-accent" />
                    <span className="data-label">Time:</span>
                    <span className="data-value">
                      {new Date(currentPoint.timestamp).toLocaleTimeString()}
                    </span>
                  </div>
                  <div className="flex items-center gap-2">
                    <span className="data-label">Alt:</span>
                    <span className="data-value">{currentPoint.altitude?.toFixed(0)} m</span>
                  </div>
                  <div className="flex items-center gap-2">
                    <span className="data-label">Speed:</span>
                    <span className="data-value">{currentPoint.groundSpeed?.toFixed(0)} m/s</span>
                  </div>
                </>
              )}
            </div>

            {currentPoint && (
              <div className={`px-3 py-1 rounded-full text-xs font-medium ${
                currentPoint.flightPhase === 'CRUISE' ? 'bg-blue-500/20 text-blue-400' :
                currentPoint.flightPhase === 'CLIMB' ? 'bg-green-500/20 text-green-400' :
                currentPoint.flightPhase === 'DESCENT' ? 'bg-yellow-500/20 text-yellow-400' :
                'bg-gray-500/20 text-gray-400'
              }`}>
                {currentPoint.flightPhase}
              </div>
            )}
          </div>
        </div>

        {/* Right Panel - Flight Info */}
        <div className="w-96 glass-panel m-3 flex flex-col overflow-hidden flex-shrink-0">
          {selectedFlight ? (
            <>
              <div className="glass-panel-header flex items-center justify-between">
                <h2 className="text-sm font-semibold text-white flex items-center gap-2">
                  <Plane className="w-4 h-4" />
                  Flight Information
                </h2>
                <span className="text-lg font-bold text-atc-accent">
                  {selectedFlight.flightId}
                </span>
              </div>

              <div className="flex-1 overflow-y-auto scrollbar-thin">
                <FlightInfoPanel
                  flight={selectedFlight}
                  currentPoint={currentPoint}
                  trajectoryPoints={trajectoryPoints}
                />

                {trajectoryPoints.length > 0 && (
                  <div className="border-t border-white/10">
                    <div className="glass-panel-header">
                      <h3 className="text-sm font-semibold text-white">Trajectory Profile</h3>
                    </div>
                    <div className="p-4">
                      <TrajectoryChart trajectoryPoints={trajectoryPoints} />
                    </div>
                  </div>
                )}
              </div>
            </>
          ) : (
            <div className="flex-1 flex items-center justify-center">
              <div className="text-center text-gray-400">
                <Plane className="w-12 h-12 mx-auto mb-3 opacity-50" />
                <p className="text-sm">Select a flight to view details</p>
              </div>
            </div>
          )}
        </div>
      </div>

      {/* Notifications */}
      <div className="fixed top-20 right-4 z-50 flex flex-col gap-2">
        {notifications.map(notification => (
          <NotificationToast
            key={notification.id}
            type={notification.type}
            message={notification.message}
            onClose={() => setNotifications(prev => prev.filter(n => n.id !== notification.id))}
          />
        ))}
      </div>

      {/* Settings Modal */}
      {showSettings && (
        <div className="fixed inset-0 bg-black/50 flex items-center justify-center z-50">
          <div className="glass-panel w-96 animate-fadeIn">
            <div className="glass-panel-header flex items-center justify-between">
              <h2 className="text-sm font-semibold text-white">Settings</h2>
              <button
                onClick={() => setShowSettings(false)}
                className="p-1 hover:bg-atc-panel-light rounded"
              >
                <X className="w-4 h-4" />
              </button>
            </div>
            <div className="p-4 space-y-4">
              <div>
                <label className="data-label block mb-1">API Base URL</label>
                <input
                  type="text"
                  defaultValue="http://localhost:8080"
                  className="input-field"
                />
              </div>
              <div>
                <label className="data-label block mb-1">WebSocket URL</label>
                <input
                  type="text"
                  defaultValue="ws://localhost:8080/ws"
                  className="input-field"
                />
              </div>
              <div>
                <label className="data-label block mb-1">Trajectory Step (seconds)</label>
                <input
                  type="number"
                  defaultValue="5"
                  className="input-field"
                />
              </div>
              <div className="flex gap-2">
                <button
                  onClick={() => setShowSettings(false)}
                  className="btn-primary flex-1"
                >
                  Save
                </button>
                <button
                  onClick={() => setShowSettings(false)}
                  className="btn-secondary flex-1"
                >
                  Cancel
                </button>
              </div>
            </div>
          </div>
        </div>
      )}
    </div>
  );
}

export default App;
