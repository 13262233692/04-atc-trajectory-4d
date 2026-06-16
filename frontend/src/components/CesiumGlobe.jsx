import { useEffect, useRef, useState, useCallback, forwardRef, useImperativeHandle } from 'react';
import * as Cesium from 'cesium';
import { PrimitiveRenderer } from '../rendering/PrimitiveRenderer';
import { PolygonDrawer } from '../rendering/PolygonDrawer';
import { RestrictedAirspaceRenderer } from '../rendering/RestrictedAirspaceRenderer';
import { format } from 'date-fns';

const CesiumGlobe = forwardRef(function CesiumGlobe({ selectedFlight, renderPipeline }, ref) {
  const containerRef = useRef(null);
  const viewerRef = useRef(null);
  const rendererRef = useRef(null);
  const drawerRef = useRef(null);
  const airspaceRendererRef = useRef(null);
  const [currentPoint, setCurrentPoint] = useState(null);
  const [renderStats, setRenderStats] = useState({ totalFlights: 0, totalPrimitives: 0 });

  useImperativeHandle(ref, () => ({
    getCurrentPoint: () => currentPoint,
    getViewer: () => viewerRef.current,
    getRenderer: () => rendererRef.current,
    getAirspaceRenderer: () => airspaceRendererRef.current,
    startPolygonDrawing: (onComplete, onCancel) => {
      if (!viewerRef.current) return;
      if (drawerRef.current) drawerRef.current.destroy();
      drawerRef.current = new PolygonDrawer(viewerRef.current, onComplete, onCancel);
      drawerRef.current.start();
    },
    cancelPolygonDrawing: () => {
      if (drawerRef.current) {
        drawerRef.current.cancel();
        drawerRef.current = null;
      }
    },
    isDrawingActive: () => drawerRef.current && drawerRef.current.active,
    renderAirspace: (airspace) => airspaceRendererRef.current?.renderAirspace(airspace),
    removeAirspace: (id) => airspaceRendererRef.current?.removeAirspace(id),
    renderRouteComparison: (original, detour) =>
      airspaceRendererRef.current?.renderRouteComparison(original, detour),
    clearRouteComparison: () => airspaceRendererRef.current?.clearRouteComparison(),
  }), [currentPoint]);

  const onFrameUpdate = useCallback((data) => {
    const renderer = rendererRef.current;
    if (!renderer) return;

    renderer.updateFlights(data.flightPositions, data.flightTrails);

    if (data.selectedPoint) {
      setCurrentPoint(data.selectedPoint);
    }

    if (data.stats) {
      setRenderStats(prev => ({
        ...prev,
        totalFlights: data.stats.totalFlights,
      }));
    }
  }, []);

  const onFlightSelected = useCallback((data) => {
  }, []);

  const onCleared = useCallback(() => {
    setCurrentPoint(null);
    setRenderStats({ totalFlights: 0, totalPrimitives: 0 });
  }, []);

  useEffect(() => {
    if (!containerRef.current || viewerRef.current) return;

    const viewer = new Cesium.Viewer(containerRef.current, {
      timeline: false,
      animation: false,
      infoBox: false,
      geocoder: false,
      homeButton: false,
      sceneModePicker: false,
      baseLayerPicker: false,
      navigationHelpButton: false,
      navigationInstructionsInitiallyVisible: false,
      shouldAnimate: true,
      skyAtmosphere: true,
      creditContainer: document.createElement('div'),
      requestRenderMode: true,
      maximumRenderTimeChange: Infinity,
      targetFrameRate: 30,
    });

    viewer.scene.globe.enableLighting = true;
    viewer.scene.globe.baseColor = Cesium.Color.BLACK;
    viewer.scene.backgroundColor = Cesium.Color.BLACK;
    viewer.scene.skyAtmosphere.show = true;
    viewer.scene.fog.enabled = true;
    viewer.scene.skyBox.show = true;

    viewer.scene.globe.tileCacheSize = 100;
    viewer.scene.logarithmicDepthBuffer = true;

    const imageryProvider = new Cesium.OpenStreetMapImageryProvider({
      url: 'https://tile.openstreetmap.org/',
    });
    viewer.imageryLayers.addImageryProvider(imageryProvider);
    viewer.imageryLayers.get(0).minimumTerrainLevel = 4;

    viewerRef.current = viewer;

    const renderer = new PrimitiveRenderer(viewer);
    rendererRef.current = renderer;

    const airspaceRenderer = new RestrictedAirspaceRenderer(viewer);
    airspaceRendererRef.current = airspaceRenderer;

    if (renderPipeline) {
      renderPipeline.init(viewer, onFrameUpdate, onFlightSelected, onCleared);
      renderPipeline.renderer = renderer;
    }

    return () => {
      if (drawerRef.current) {
        drawerRef.current.destroy();
        drawerRef.current = null;
      }
      if (airspaceRenderer) {
        airspaceRenderer.destroy();
      }
      if (renderPipeline) {
        renderPipeline.destroy();
      }
      if (renderer) {
        renderer.destroy();
      }
      if (viewer && !viewer.isDestroyed()) {
        viewer.destroy();
      }
      viewerRef.current = null;
      rendererRef.current = null;
      airspaceRendererRef.current = null;
    };
  }, []);

  useEffect(() => {
    if (selectedFlight && viewerRef.current) {
      const viewer = viewerRef.current;

      if (selectedFlight.waypoints && rendererRef.current) {
        rendererRef.current.updateWaypoints(selectedFlight.waypoints);
      }

      if (renderPipeline) {
        renderPipeline.selectFlight(selectedFlight.flightId);
      }

      if (selectedFlight.waypoints && selectedFlight.waypoints.length > 0) {
        const first = selectedFlight.waypoints[0];
        const last = selectedFlight.waypoints[selectedFlight.waypoints.length - 1];
        const midLat = (first.latitude + last.latitude) / 2;
        const midLon = (first.longitude + last.longitude) / 2;

        viewer.camera.flyTo({
          destination: Cesium.Cartesian3.fromDegrees(midLon, midLat, 500000),
          orientation: {
            heading: Cesium.Math.toRadians(0),
            pitch: Cesium.Math.toRadians(-45),
            roll: 0,
          },
          duration: 2,
        });
      }
    }
  }, [selectedFlight, renderPipeline]);

  useEffect(() => {
    const interval = setInterval(() => {
      if (rendererRef.current) {
        const stats = rendererRef.current.getStats();
        setRenderStats(prev => ({
          ...prev,
          totalPrimitives: stats.totalPrimitives,
        }));
      }
    }, 2000);

    return () => clearInterval(interval);
  }, []);

  return (
    <div className="w-full h-full relative">
      <div ref={containerRef} className="w-full h-full" />

      {currentPoint && (
        <div className="absolute top-4 left-4 glass-panel p-3 text-sm animate-fadeIn">
          <div className="space-y-2">
            <div className="flex justify-between gap-4">
              <span className="text-gray-400">Latitude</span>
              <span className="text-white font-mono">{currentPoint.latitude?.toFixed(6)}°</span>
            </div>
            <div className="flex justify-between gap-4">
              <span className="text-gray-400">Longitude</span>
              <span className="text-white font-mono">{currentPoint.longitude?.toFixed(6)}°</span>
            </div>
            <div className="flex justify-between gap-4">
              <span className="text-gray-400">Altitude</span>
              <span className="text-white font-mono">{currentPoint.altitude?.toFixed(1)} m</span>
            </div>
            <div className="border-t border-white/10 pt-2 mt-2">
              <div className="flex justify-between gap-4">
                <span className="text-gray-400">Time</span>
                <span className="text-white font-mono">
                  {currentPoint.timestamp ? format(new Date(currentPoint.timestamp), 'HH:mm:ss') : '-'}
                </span>
              </div>
            </div>
          </div>
        </div>
      )}

      <div className="absolute top-4 right-4 glass-panel p-2 text-xs space-y-1">
        <div className="flex items-center gap-2">
          <div className="w-2 h-2 rounded-full bg-green-400 animate-pulse"></div>
          <span className="text-gray-300">Flights: <span className="text-white font-mono">{renderStats.totalFlights}</span></span>
        </div>
        <div className="flex items-center gap-2">
          <div className="w-2 h-2 rounded-full bg-cyan-400"></div>
          <span className="text-gray-300">Primitives: <span className="text-white font-mono">{renderStats.totalPrimitives}</span></span>
        </div>
      </div>

      <div className="absolute bottom-20 right-4 glass-panel p-3 text-xs">
        <div className="font-semibold text-white mb-2">Flight Phase Legend</div>
        <div className="space-y-1">
          <div className="flex items-center gap-2">
            <div className="w-3 h-3 rounded-full bg-orange-400"></div>
            <span className="text-gray-300">Takeoff</span>
          </div>
          <div className="flex items-center gap-2">
            <div className="w-3 h-3 rounded-full bg-green-400"></div>
            <span className="text-gray-300">Climb</span>
          </div>
          <div className="flex items-center gap-2">
            <div className="w-3 h-3 rounded-full bg-cyan-400"></div>
            <span className="text-gray-300">Cruise</span>
          </div>
          <div className="flex items-center gap-2">
            <div className="w-3 h-3 rounded-full bg-yellow-400"></div>
            <span className="text-gray-300">Descent</span>
          </div>
          <div className="flex items-center gap-2">
            <div className="w-3 h-3 rounded-full bg-red-400"></div>
            <span className="text-gray-300">Landing</span>
          </div>
        </div>
      </div>
    </div>
  );
});

export default CesiumGlobe;
