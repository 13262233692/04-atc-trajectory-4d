import { useState, useEffect, useCallback, useRef, useMemo } from 'react';
import { Plane, Activity, MapPin, Clock, Settings, X, Play, RefreshCw, Cpu, Eye, EyeOff, AlertTriangle } from 'lucide-react';
import CesiumGlobe from './components/CesiumGlobe';
import FlightList from './components/FlightList';
import FlightInfoPanel from './components/FlightInfoPanel';
import TrajectoryChart from './components/TrajectoryChart';
import NotificationToast from './components/NotificationToast';
import AirspacePanel from './components/AirspacePanel';
import {
  fetchAllFlights, calculateTrajectory, startStreaming, stopStreaming,
  fetchAllAirspaces, createAirspace, deleteAirspace, triggerAirspaceAvoidance, rerouteFlight,
} from './services/api';
import { useWebSocket } from './services/websocket';
import { RenderPipeline } from './rendering/RenderPipeline';
import { FlightTrajectoryBuffer } from './rendering/RingBuffer';

function App() {
  const [flights, setFlights] = useState([]);
  const [selectedFlight, setSelectedFlight] = useState(null);
  const [currentPoint, setCurrentPoint] = useState(null);
  const [trajectoryHistory, setTrajectoryHistory] = useState([]);
  const [isStreaming, setIsStreaming] = useState(false);
  const [isCalculating, setIsCalculating] = useState(false);
  const [notifications, setNotifications] = useState([]);
  const [showSettings, setShowSettings] = useState(false);
  const [cullingEnabled, setCullingEnabled] = useState(true);
  const [pipelineStats, setPipelineStats] = useState({ totalFlights: 0, totalPrimitives: 0 });
  const [rightPanelTab, setRightPanelTab] = useState('flight');
  const [drawingPolygon, setDrawingPolygon] = useState(false);
  const [pendingVertices, setPendingVertices] = useState(null);
  const [airspaces, setAirspaces] = useState([]);
  const [rerouteResult, setRerouteResult] = useState(null);

  const cesiumRef = useRef(null);
  const pipelineRef = useRef(null);
  const historyBufferRef = useRef(null);

  const { connect, disconnect, subscribe, isConnected } = useWebSocket();

  useEffect(() => {
    const pipeline = new RenderPipeline();
    pipelineRef.current = pipeline;

    return () => {
      pipeline.destroy();
      pipelineRef.current = null;
    };
  }, []);

  const addNotification = useCallback((type, message) => {
    const id = Date.now();
    setNotifications(prev => [...prev, { id, type, message }]);
    setTimeout(() => {
      setNotifications(prev => prev.filter(n => n.id !== id));
    }, 5000);
  }, []);

  useEffect(() => {
    loadFlights();
    loadAirspaces();
    connect();

    subscribe('/topic/notifications', (message) => {
      try {
        const payload = JSON.parse(message.body);
        if (payload && typeof payload === 'object' && payload.type) {
          handleAirspaceNotification(payload);
        } else {
          addNotification('info', message.body);
        }
      } catch {
        addNotification('info', message.body);
      }
    });

    return () => {
      disconnect();
    };
  }, [connect, disconnect, subscribe, addNotification]);

  const loadAirspaces = async () => {
    const list = await fetchAllAirspaces();
    setAirspaces(list || []);
    if (cesiumRef.current) {
      list.forEach(a => cesiumRef.current.renderAirspace(a));
    }
  };

  const handleAirspaceNotification = (payload) => {
    switch (payload.type) {
      case 'AIRSPACE_CREATED':
        if (payload.airspace) {
          setAirspaces(prev => [...prev.filter(a => a.id !== payload.airspace.id), payload.airspace]);
          cesiumRef.current?.renderAirspace(payload.airspace);
          addNotification('warning', `Restricted zone created: ${payload.airspace.name || payload.airspace.id}`);
        }
        break;
      case 'AIRSPACE_REMOVED':
        setAirspaces(prev => prev.filter(a => a.id !== payload.airspaceId));
        cesiumRef.current?.removeAirspace(payload.airspaceId);
        addNotification('info', `Restricted zone removed: ${payload.airspaceId}`);
        break;
      case 'REROUTE_COMPLETE': {
        const result = {
          flightId: payload.flightId,
          success: payload.success,
          extraDistanceMeters: payload.extraDistanceMeters,
          extraTimeSeconds: payload.extraTimeSeconds,
          detourRoute: payload.detourWaypoints,
        };
        setRerouteResult(result);
        if (payload.detourWaypoints && selectedFlight && selectedFlight.flightId === payload.flightId) {
          cesiumRef.current?.renderRouteComparison(selectedFlight.waypoints, payload.detourWaypoints);
        }
        addNotification(
          payload.success ? 'success' : 'error',
          `Reroute ${payload.success ? 'successful' : 'failed'} for ${payload.flightId}`
        );
        break;
      }
      default:
        addNotification('info', JSON.stringify(payload));
    }
  };

  useEffect(() => {
    if (!selectedFlight || !isConnected) return;

    const trajectorySub = subscribe(`/topic/trajectory/${selectedFlight.flightId}`, (message) => {
      try {
        const point = JSON.parse(message.body);

        if (pipelineRef.current) {
          pipelineRef.current.feedTrajectoryPoint(point);
        }

        setCurrentPoint(point);
        if (historyBufferRef.current) {
          historyBufferRef.current.update(point, null);
        }
      } catch (e) {
        console.error('Error parsing trajectory point:', e);
      }
    });

    const batchSub = subscribe('/topic/trajectory/batch', (message) => {
      try {
        const points = JSON.parse(message.body);
        if (Array.isArray(points) && pipelineRef.current) {
          pipelineRef.current.feedTrajectoryBatch(points);
        }
      } catch (e) {
        console.error('Error parsing trajectory batch:', e);
      }
    });

    const statusSub = subscribe(`/topic/status/${selectedFlight.flightId}`, (message) => {
      const status = message.body;
      if (status === 'COMPLETED') {
        setIsStreaming(false);
        addNotification('success', `Flight ${selectedFlight.flightId} trajectory calculation completed`);
      }
    });

    return () => {
      trajectorySub.unsubscribe();
      try { batchSub.unsubscribe(); } catch (e) {}
      statusSub.unsubscribe();
    };
  }, [selectedFlight, isConnected, subscribe, addNotification]);

  useEffect(() => {
    const interval = setInterval(() => {
      if (pipelineRef.current) {
        pipelineRef.current.getStats().then(stats => {
          setPipelineStats(prev => ({
            ...prev,
            totalFlights: stats.totalFlights || 0,
            totalPrimitives: stats.rendererStats?.totalPrimitives || 0,
          }));
        }).catch(() => {});
      }
    }, 3000);

    return () => clearInterval(interval);
  }, []);

  useEffect(() => {
    if (pipelineRef.current) {
      pipelineRef.current.setCullingEnabled(cullingEnabled);
    }
  }, [cullingEnabled]);

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
    setCurrentPoint(null);
    setTrajectoryHistory([]);

    historyBufferRef.current = new FlightTrajectoryBuffer(flight.flightId, 720, 120);

    if (pipelineRef.current) {
      pipelineRef.current.selectFlight(flight.flightId);
    }
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
      setCurrentPoint(null);
      setTrajectoryHistory([]);
      if (historyBufferRef.current) {
        historyBufferRef.current.clear();
      }
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
    setCurrentPoint(null);
    setTrajectoryHistory([]);
    if (historyBufferRef.current) {
      historyBufferRef.current.clear();
    }
    if (pipelineRef.current) {
      pipelineRef.current.clearFlight(selectedFlight?.flightId);
    }
  };

  const handleStartDrawing = () => {
    if (!cesiumRef.current) return;
    setDrawingPolygon(true);
    setPendingVertices(null);
    setRerouteResult(null);
    cesiumRef.current.startPolygonDrawing(
      (vertices) => {
        setDrawingPolygon(false);
        setPendingVertices(vertices);
        addNotification('info', `Polygon defined with ${vertices.length} vertices — submit the zone`);
      },
      () => {
        setDrawingPolygon(false);
        setPendingVertices(null);
      }
    );
  };

  const handleCancelDrawing = () => {
    cesiumRef.current?.cancelPolygonDrawing();
    setDrawingPolygon(false);
    setPendingVertices(null);
  };

  const handleSubmitAirspace = async (payload) => {
    try {
      const created = await createAirspace(payload);
      setPendingVertices(null);
      if (created) {
        setAirspaces(prev => [...prev.filter(a => a.id !== created.id), created]);
        cesiumRef.current?.renderAirspace(created);
        addNotification('success', `Restricted zone created: ${created.name || created.id}`);
      }
      return created;
    } catch (e) {
      addNotification('error', 'Failed to create restricted zone');
      throw e;
    }
  };

  const handleDeleteAirspace = async (airspaceId) => {
    try {
      await deleteAirspace(airspaceId);
      setAirspaces(prev => prev.filter(a => a.id !== airspaceId));
      cesiumRef.current?.removeAirspace(airspaceId);
      addNotification('info', `Restricted zone deleted: ${airspaceId}`);
    } catch (e) {
      addNotification('error', 'Failed to delete restricted zone');
    }
  };

  const handleTriggerAvoidance = async (airspaceId) => {
    try {
      await triggerAirspaceAvoidance(airspaceId);
      addNotification('info', `Reroute triggered for zone ${airspaceId} — check notifications`);
    } catch (e) {
      addNotification('error', 'Failed to trigger avoidance');
    }
  };

  const trajectoryPointsForChart = useMemo(() => {
    if (historyBufferRef.current) {
      return historyBufferRef.current.getHistoryArray();
    }
    return [];
  }, [currentPoint]);

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
              {isConnected ? 'WS Connected' : 'WS Disconnected'}
            </span>
          </div>

          <div className="flex items-center gap-2 px-3 py-1.5 bg-atc-panel-light rounded-md">
            <Cpu className="w-4 h-4 text-cyan-400" />
            <span className="text-sm text-gray-300">
              {pipelineStats.totalFlights} flights | {pipelineStats.totalPrimitives} primitives
            </span>
          </div>

          <button
            onClick={() => setCullingEnabled(!cullingEnabled)}
            className={`p-2 rounded-md transition-colors ${cullingEnabled ? 'bg-atc-success/20 text-atc-success' : 'bg-atc-panel-light text-gray-400'}`}
            title={cullingEnabled ? 'Frustum Culling: ON' : 'Frustum Culling: OFF'}
          >
            {cullingEnabled ? <Eye className="w-5 h-5" /> : <EyeOff className="w-5 h-5" />}
          </button>

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
            ref={cesiumRef}
            selectedFlight={selectedFlight}
            renderPipeline={pipelineRef.current}
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
                Calculate
              </button>

              {!isStreaming ? (
                <button
                  onClick={handleStartStreaming}
                  className="btn-success flex items-center gap-2"
                >
                  <Play className="w-4 h-4" />
                  Stream
                </button>
              ) : (
                <button
                  onClick={handleStopStreaming}
                  className="btn-danger flex items-center gap-2"
                >
                  <X className="w-4 h-4" />
                  Stop
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
                <span className="data-label">Flights:</span>
                <span className="data-value">{pipelineStats.totalFlights}</span>
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

            <div className="flex items-center gap-4">
              <div className="flex items-center gap-1 text-xs">
                <Cpu className="w-3 h-3 text-cyan-400" />
                <span className="text-gray-400">Worker</span>
              </div>
              <div className="flex items-center gap-1 text-xs">
                {cullingEnabled ? <Eye className="w-3 h-3 text-green-400" /> : <EyeOff className="w-3 h-3 text-gray-400" />}
                <span className="text-gray-400">Culling</span>
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
        </div>

        {/* Right Panel - Flight Info / Airspace */}
        <div className="w-96 glass-panel m-3 flex flex-col overflow-hidden flex-shrink-0">
          <div className="flex border-b border-white/10">
            <button
              onClick={() => setRightPanelTab('flight')}
              className={`flex-1 py-2.5 text-sm font-medium flex items-center justify-center gap-2 transition ${
                rightPanelTab === 'flight'
                  ? 'bg-white/5 text-atc-accent border-b-2 border-atc-accent'
                  : 'text-gray-400 hover:text-white hover:bg-white/5'
              }`}
            >
              <Plane className="w-4 h-4" /> Flight
            </button>
            <button
              onClick={() => setRightPanelTab('airspace')}
              className={`flex-1 py-2.5 text-sm font-medium flex items-center justify-center gap-2 transition relative ${
                rightPanelTab === 'airspace'
                  ? 'bg-white/5 text-red-400 border-b-2 border-red-400'
                  : 'text-gray-400 hover:text-white hover:bg-white/5'
              }`}
            >
              <AlertTriangle className="w-4 h-4" /> Airspace
              {airspaces.length > 0 && (
                <span className="absolute top-1.5 right-3 w-4 h-4 text-[10px] bg-red-500 text-white rounded-full flex items-center justify-center font-bold">
                  {airspaces.length}
                </span>
              )}
            </button>
          </div>

          <div className="flex-1 overflow-hidden">
            {rightPanelTab === 'flight' ? (
              selectedFlight ? (
                <div className="h-full flex flex-col overflow-hidden">
                  <div className="glass-panel-header flex items-center justify-between px-4">
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
                      trajectoryPoints={trajectoryPointsForChart}
                    />

                    {trajectoryPointsForChart.length > 0 && (
                      <div className="border-t border-white/10">
                        <div className="glass-panel-header">
                          <h3 className="text-sm font-semibold text-white">Trajectory Profile</h3>
                        </div>
                        <div className="p-4">
                          <TrajectoryChart trajectoryPoints={trajectoryPointsForChart} />
                        </div>
                      </div>
                    )}

                    <div className="p-4 border-t border-white/10">
                      <button
                        onClick={async () => {
                          try {
                            const result = await rerouteFlight(selectedFlight.flightId);
                            setRerouteResult(result);
                            if (result?.success && result.detourRoute) {
                              cesiumRef.current?.renderRouteComparison(selectedFlight.waypoints, result.detourRoute);
                              addNotification('success', `Rerouted ${selectedFlight.flightId}: +${result.extraDistanceMeters?.toFixed(0)}m`);
                            } else {
                              addNotification('error', result?.message || 'Reroute failed');
                            }
                          } catch (e) {
                            addNotification('error', 'Reroute request failed');
                          }
                        }}
                        className="w-full bg-amber-600 hover:bg-amber-500 text-white py-2 rounded-lg font-semibold flex items-center justify-center gap-2"
                      >
                        <AlertTriangle className="w-4 h-4" /> Manual Reroute (Check Airspace)
                      </button>
                    </div>
                  </div>
                </div>
              ) : (
                <div className="h-full flex items-center justify-center">
                  <div className="text-center text-gray-400 px-4">
                    <Plane className="w-12 h-12 mx-auto mb-3 opacity-50" />
                    <p className="text-sm">Select a flight to view details</p>
                  </div>
                </div>
              )
            ) : (
              <AirspacePanel
                drawing={drawingPolygon}
                onStartDrawing={handleStartDrawing}
                onCancelDrawing={handleCancelDrawing}
                pendingVertices={pendingVertices}
                onSubmit={handleSubmitAirspace}
                airspaces={airspaces}
                onDeleteAirspace={handleDeleteAirspace}
                onTriggerAvoidance={handleTriggerAvoidance}
                rerouteResult={rerouteResult}
              />
            )}
          </div>
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
              <h2 className="text-sm font-semibold text-white">Performance Settings</h2>
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
              <div className="flex items-center justify-between">
                <label className="data-label">Frustum Culling</label>
                <button
                  onClick={() => setCullingEnabled(!cullingEnabled)}
                  className={`px-3 py-1 rounded text-sm ${cullingEnabled ? 'bg-atc-success text-white' : 'bg-atc-panel-light text-gray-400'}`}
                >
                  {cullingEnabled ? 'ON' : 'OFF'}
                </button>
              </div>
              <div>
                <label className="data-label block mb-1">Max Flights (Worker)</label>
                <input
                  type="number"
                  defaultValue="2000"
                  className="input-field"
                />
              </div>
              <div>
                <label className="data-label block mb-1">Trail Length (positions)</label>
                <input
                  type="number"
                  defaultValue="720"
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
