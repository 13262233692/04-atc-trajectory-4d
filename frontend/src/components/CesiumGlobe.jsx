import { useEffect, useRef, useState, useMemo } from 'react';
import * as Cesium from 'cesium';
import { Viewer, Entity, PolylineGraphics, PointGraphics, LabelGraphics, ModelGraphics, PathGraphics } from 'resium';
import { format } from 'date-fns';

function CesiumGlobe({ trajectoryPoints, currentPoint, selectedFlight }) {
  const viewerRef = useRef(null);
  const [viewerReady, setViewerReady] = useState(false);

  useEffect(() => {
    if (viewerRef.current && !viewerReady) {
      const viewer = viewerRef.current.cesiumElement;
      
      viewer.scene.globe.enableLighting = true;
      viewer.scene.globe.baseColor = Cesium.Color.BLACK;
      viewer.scene.backgroundColor = Cesium.Color.BLACK;
      viewer.scene.skyAtmosphere.show = true;
      viewer.scene.fog.enabled = true;
      viewer.scene.skyBox.show = true;

      viewer.infoBox.show = false;
      if (viewer.geocoder) viewer.geocoder.viewModel.show = false;
      if (viewer.homeButton) viewer.homeButton.viewModel.show = false;
      if (viewer.sceneModePicker) viewer.sceneModePicker.viewModel.show = false;
      if (viewer.baseLayerPicker) viewer.baseLayerPicker.viewModel.show = false;
      if (viewer.navigationHelpButton) viewer.navigationHelpButton.viewModel.show = false;

      const imageryProvider = new Cesium.OpenStreetMapImageryProvider({
        url: 'https://tile.openstreetmap.org/',
      });
      viewer.imageryLayers.addImageryProvider(imageryProvider);

      setViewerReady(true);
    }
  }, [viewerReady]);

  useEffect(() => {
    if (viewerRef.current && trajectoryPoints.length > 0) {
      const viewer = viewerRef.current.cesiumElement;
      
      const lastPoint = trajectoryPoints[trajectoryPoints.length - 1];
      if (lastPoint) {
        viewer.camera.flyTo({
          destination: Cesium.Cartesian3.fromDegrees(
            lastPoint.longitude,
            lastPoint.latitude,
            (lastPoint.altitude || 0) + 50000,
          ),
          orientation: {
            heading: Cesium.Math.toRadians(0),
            pitch: Cesium.Math.toRadians(-45),
            roll: 0,
          },
          duration: 2,
        });
      }
    }
  }, [selectedFlight]);

  const trajectoryPositions = useMemo(() => {
    if (trajectoryPoints.length < 2) {
      return [];
    }

    return trajectoryPoints
      .filter(point => point.longitude && point.latitude && point.altitude !== undefined)
      .map(point => Cesium.Cartesian3.fromDegrees(
        point.longitude,
        point.latitude,
        Math.max(0, point.altitude)
      ));
  }, [trajectoryPoints]);

  const trailPositions = useMemo(() => {
    if (trajectoryPoints.length < 2) {
      return [];
    }

    const startIdx = Math.max(0, trajectoryPoints.length - 100);
    return trajectoryPoints
      .slice(startIdx)
      .filter(point => point.longitude && point.latitude && point.altitude !== undefined)
      .map(point => Cesium.Cartesian3.fromDegrees(
        point.longitude,
        point.latitude,
        Math.max(0, point.altitude)
      ));
  }, [trajectoryPoints]);

  const waypointEntities = useMemo(() => {
    if (!selectedFlight || !selectedFlight.waypoints) {
      return [];
    }

    return selectedFlight.waypoints.map((waypoint, index) => ({
      id: `waypoint-${index}`,
      name: waypoint.name,
      position: Cesium.Cartesian3.fromDegrees(
        waypoint.longitude,
        waypoint.latitude,
        waypoint.altitude || 5000
      ),
    }));
  }, [selectedFlight]);

  const getFlightPhaseColor = (flightPhase) => {
    switch (flightPhase) {
      case 'TAKEOFF':
        return Cesium.Color.ORANGE;
      case 'CLIMB':
        return Cesium.Color.GREEN;
      case 'CRUISE':
        return Cesium.Color.CYAN;
      case 'DESCENT':
        return Cesium.Color.YELLOW;
      case 'LANDING':
        return Cesium.Color.RED;
      default:
        return Cesium.Color.WHITE;
    }
  };

  const trajectoryEntity = {
    id: 'trajectory-line',
    polyline: {
      positions: trajectoryPositions,
      width: 3,
      material: Cesium.Color.YELLOW.withAlpha(0.6),
      clampToGround: false,
    },
  };

  const trailEntity = {
    id: 'trail-line',
    polyline: {
      positions: trailPositions,
      width: 5,
      material: Cesium.Color.CYAN.withAlpha(0.8),
      clampToGround: false,
    },
  };

  return (
    <div className="w-full h-full relative">
      <Viewer
        ref={viewerRef}
        full
        timeline={false}
        animation={false}
        infoBox={false}
        geocoder={false}
        homeButton={false}
        sceneModePicker={false}
        baseLayerPicker={false}
        navigationHelpButton={false}
        navigationInstructionsInitiallyVisible={false}
        shouldAnimate={true}
        skyAtmosphere
        creditContainer="hidden"
      >
        {trajectoryPositions.length >= 2 && (
          <Entity {...trajectoryEntity} />
        )}

        {trailPositions.length >= 2 && (
          <Entity {...trailEntity} />
        )}

        {trajectoryPoints.map((point, index) => (
          point.longitude && point.latitude && (
            <Entity
              key={`point-${index}`}
              position={Cesium.Cartesian3.fromDegrees(
                point.longitude,
                point.latitude,
                Math.max(0, point.altitude || 0)
              )}
            >
              <PointGraphics
                pixelSize={3}
                color={getFlightPhaseColor(point.flightPhase)}
                outlineColor={Cesium.Color.WHITE}
                outlineWidth={1}
              />
            </Entity>
          )
        ))}

        {waypointEntities.map((waypoint) => (
          <Entity
            key={waypoint.id}
            position={waypoint.position}
          >
            <PointGraphics
              pixelSize={8}
              color={Cesium.Color.RED}
              outlineColor={Cesium.Color.WHITE}
              outlineWidth={2}
            />
            <LabelGraphics
              text={waypoint.name}
              font="bold 14px sans-serif"
              fillColor={Cesium.Color.WHITE}
              outlineColor={Cesium.Color.BLACK}
              outlineWidth={2}
              style={Cesium.LabelStyle.FILL_AND_OUTLINE}
              pixelOffset={new Cesium.Cartesian2(0, -20)}
            />
          </Entity>
        ))}

        {currentPoint && currentPoint.longitude && currentPoint.latitude && (
          <Entity
            id="current-aircraft"
            position={Cesium.Cartesian3.fromDegrees(
              currentPoint.longitude,
              currentPoint.latitude,
              Math.max(0, currentPoint.altitude || 0)
            )}
            orientation={Cesium.Transforms.headingPitchRollQuaternion(
              Cesium.Cartesian3.fromDegrees(
                currentPoint.longitude,
                currentPoint.latitude,
                Math.max(0, currentPoint.altitude || 0)
              ),
              new Cesium.HeadingPitchRoll(
                Cesium.Math.toRadians(currentPoint.heading || 0),
                Cesium.Math.toRadians(0),
                0
              )
            )}
          >
            <ModelGraphics
              uri="https://assets.cesium.com/cesiumjs/SampleData/models/CesiumAir/Cesium_Air.glb"
              minimumPixelSize={64}
              maximumScale={200}
              color={getFlightPhaseColor(currentPoint.flightPhase)}
              colorBlendMode={Cesium.ColorBlendMode.MIX}
              colorBlendAmount={0.5}
              heightReference={Cesium.HeightReference.NONE}
            />

            <PointGraphics
              pixelSize={12}
              color={getFlightPhaseColor(currentPoint.flightPhase)}
              outlineColor={Cesium.Color.WHITE}
              outlineWidth={2}
              heightReference={Cesium.HeightReference.NONE}
            />

            <LabelGraphics
              text={selectedFlight ? selectedFlight.flightId : ''}
              font="bold 16px sans-serif"
              fillColor={Cesium.Color.WHITE}
              outlineColor={Cesium.Color.BLACK}
              outlineWidth={3}
              style={Cesium.LabelStyle.FILL_AND_OUTLINE}
              pixelOffset={new Cesium.Cartesian2(0, -40)}
              heightReference={Cesium.HeightReference.NONE}
            />

            <PathGraphics
              leadTime={0}
              trailTime={60}
              width={2}
              resolution={1}
              material={getFlightPhaseColor(currentPoint.flightPhase)}
            />
          </Entity>
        )}
      </Viewer>

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
}

export default CesiumGlobe;
