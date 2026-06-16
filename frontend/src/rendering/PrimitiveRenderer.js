import * as Cesium from 'cesium';

const PHASE_COLORS = [
  Cesium.Color.ORANGE,
  Cesium.Color.GREEN,
  Cesium.Color.CYAN,
  Cesium.Color.YELLOW,
  Cesium.Color.RED,
  Cesium.Color.WHITE,
];

const PHASE_COLORS_ALPHA = PHASE_COLORS.map(c => c.withAlpha(0.7));

export class PrimitiveRenderer {
  constructor(viewer) {
    this.viewer = viewer;
    this.scene = viewer.scene;

    this.pointCollection = new Cesium.PointPrimitiveCollection();
    this.scene.primitives.add(this.pointCollection);

    this.polylineCollection = new Cesium.PolylineCollection();
    this.scene.primitives.add(this.polylineCollection);

    this.labelCollection = new Cesium.LabelCollection();
    this.scene.primitives.add(this.labelCollection);

    this.billboardCollection = new Cesium.BillboardCollection();
    this.scene.primitives.add(this.billboardCollection);

    this.flightPointMap = new Map();
    this.flightTrailMap = new Map();
    this.flightLabelMap = new Map();

    this.waypointPointMap = new Map();
    this.waypointLabelMap = new Map();

    this.selectedFlightId = null;
    this.selectedEntity = null;

    this._cullingEnabled = true;
    this._lastCullTime = 0;
    this._cullInterval = 500;

    this._pendingUpdate = null;
    this._rafId = null;
  }

  updateFlights(flightPositions, flightTrails) {
    this._pendingUpdate = { flightPositions, flightTrails };
    if (!this._rafId) {
      this._rafId = requestAnimationFrame(() => this._flushUpdate());
    }
  }

  _flushUpdate() {
    this._rafId = null;
    if (!this._pendingUpdate) return;

    const { flightPositions, flightTrails } = this._pendingUpdate;
    this._pendingUpdate = null;

    const activeFlightIds = new Set();

    for (let i = 0; i < flightPositions.length; i++) {
      const fp = flightPositions[i];
      const flightId = fp.flightId;
      activeFlightIds.add(flightId);

      const cartesian = new Cesium.Cartesian3(fp.x, fp.y, fp.z);
      const colorIndex = fp.colorIndex >= 0 && fp.colorIndex < PHASE_COLORS.length ? fp.colorIndex : 5;
      const color = PHASE_COLORS_ALPHA[colorIndex];

      let point = this.flightPointMap.get(flightId);
      if (point && !point.isDestroyed()) {
        point.position = cartesian;
        point.color = color;
      } else {
        point = this.pointCollection.add({
          position: cartesian,
          pixelSize: 6,
          color: color,
          outlineColor: Cesium.Color.WHITE.withAlpha(0.3),
          outlineWidth: 1,
          id: flightId,
        });
        this.flightPointMap.set(flightId, point);
      }

      if (this.selectedFlightId === flightId) {
        let label = this.flightLabelMap.get(flightId);
        if (!label || label.isDestroyed()) {
          label = this.labelCollection.add({
            position: cartesian,
            text: flightId,
            font: 'bold 14px sans-serif',
            fillColor: Cesium.Color.WHITE,
            outlineColor: Cesium.Color.BLACK,
            outlineWidth: 2,
            style: Cesium.LabelStyle.FILL_AND_OUTLINE,
            pixelOffset: new Cesium.Cartesian2(0, -20),
            scaleByDistance: new Cesium.NearFarScalar(1e4, 1.0, 1e7, 0.3),
            id: `label-${flightId}`,
          });
          this.flightLabelMap.set(flightId, label);
        } else {
          label.position = cartesian;
        }
      }
    }

    for (let i = 0; i < flightTrails.length; i++) {
      const trail = flightTrails[i];
      const flightId = trail.flightId;

      if (this.selectedFlightId !== flightId) continue;

      const posArray = trail.positions;
      const positions = [];
      for (let j = 0; j < posArray.length; j += 3) {
        positions.push(new Cesium.Cartesian3(posArray[j], posArray[j + 1], posArray[j + 2]));
      }

      if (positions.length < 2) continue;

      const colorIndex = trail.colorIndex >= 0 && trail.colorIndex < PHASE_COLORS.length ? trail.colorIndex : 5;

      let polyline = this.flightTrailMap.get(flightId);
      if (polyline && !polyline.isDestroyed()) {
        polyline.positions = positions;
        polyline.material.uniforms.color = Cesium.Color.CYAN.withAlpha(0.8);
      } else {
        polyline = this.polylineCollection.add({
          positions: positions,
          width: 3,
          material: new Cesium.PolylineGlowMaterialProperty({
            color: Cesium.Color.CYAN.withAlpha(0.8),
          }).getValue(Cesium.JulianDate.now()),
        });
        this.flightTrailMap.set(flightId, polyline);
      }
    }

    if (activeFlightIds.size > 200) {
      this._cleanupStaleFlights(activeFlightIds);
    }
  }

  _cleanupStaleFlights(activeFlightIds) {
    for (const [flightId, point] of this.flightPointMap) {
      if (!activeFlightIds.has(flightId)) {
        if (point && !point.isDestroyed()) {
          this.pointCollection.remove(point);
        }
        this.flightPointMap.delete(flightId);

        const label = this.flightLabelMap.get(flightId);
        if (label && !label.isDestroyed()) {
          this.labelCollection.remove(label);
        }
        this.flightLabelMap.delete(flightId);

        const trail = this.flightTrailMap.get(flightId);
        if (trail && !trail.isDestroyed()) {
          this.polylineCollection.remove(trail);
        }
        this.flightTrailMap.delete(flightId);
      }
    }
  }

  setSelectedFlight(flightId) {
    if (this.selectedFlightId && this.selectedFlightId !== flightId) {
      const oldLabel = this.flightLabelMap.get(this.selectedFlightId);
      if (oldLabel && !oldLabel.isDestroyed()) {
        this.labelCollection.remove(oldLabel);
        this.flightLabelMap.delete(this.selectedFlightId);
      }
      const oldTrail = this.flightTrailMap.get(this.selectedFlightId);
      if (oldTrail && !oldTrail.isDestroyed()) {
        this.polylineCollection.remove(oldTrail);
        this.flightTrailMap.delete(this.selectedFlightId);
      }
    }
    this.selectedFlightId = flightId;
  }

  updateWaypoints(waypoints) {
    for (const [key, point] of this.waypointPointMap) {
      if (point && !point.isDestroyed()) {
        this.pointCollection.remove(point);
      }
    }
    this.waypointPointMap.clear();

    for (const [key, label] of this.waypointLabelMap) {
      if (label && !label.isDestroyed()) {
        this.labelCollection.remove(label);
      }
    }
    this.waypointLabelMap.clear();

    if (!waypoints || waypoints.length === 0) return;

    for (let i = 0; i < waypoints.length; i++) {
      const wp = waypoints[i];
      if (!wp.longitude || !wp.latitude) continue;

      const position = Cesium.Cartesian3.fromDegrees(
        wp.longitude,
        wp.latitude,
        wp.altitude || 5000
      );

      const point = this.pointCollection.add({
        position: position,
        pixelSize: 8,
        color: Cesium.Color.RED,
        outlineColor: Cesium.Color.WHITE,
        outlineWidth: 2,
        id: `wp-${i}`,
      });
      this.waypointPointMap.set(`wp-point-${i}`, point);

      const label = this.labelCollection.add({
        position: position,
        text: wp.name,
        font: 'bold 12px sans-serif',
        fillColor: Cesium.Color.WHITE,
        outlineColor: Cesium.Color.BLACK,
        outlineWidth: 2,
        style: Cesium.LabelStyle.FILL_AND_OUTLINE,
        pixelOffset: new Cesium.Cartesian2(0, -18),
        scaleByDistance: new Cesium.NearFarScalar(1e4, 1.0, 5e6, 0.4),
        id: `wp-label-${i}`,
      });
      this.waypointLabelMap.set(`wp-label-${i}`, label);
    }
  }

  performFrustumCulling(camera) {
    if (!this._cullingEnabled) return;

    const now = Date.now();
    if (now - this._lastCullTime < this._cullInterval) return;
    this._lastCullTime = now;

    const frustum = camera.frustum;
    const viewMatrix = camera.viewMatrix;
    const cullingVolume = frustum.computeCullingVolume(
      camera.position,
      camera.direction,
      camera.up
    );

    let visibleCount = 0;
    let culledCount = 0;

    for (const [flightId, point] of this.flightPointMap) {
      if (!point || point.isDestroyed()) continue;

      const position = point.position;
      const sphere = new Cesium.BoundingSphere(position, 1000);
      const visibility = cullingVolume.computeVisibility(sphere);

      if (visibility === Cesium.Intersect.INSIDE || visibility === Cesium.Intersect.INTERSECTING) {
        point.show = true;
        visibleCount++;
      } else {
        point.show = false;
        culledCount++;
      }
    }

    return { visibleCount, culledCount };
  }

  clearAll() {
    this.pointCollection.removeAll();
    this.polylineCollection.removeAll();
    this.labelCollection.removeAll();

    this.flightPointMap.clear();
    this.flightTrailMap.clear();
    this.flightLabelMap.clear();
    this.waypointPointMap.clear();
    this.waypointLabelMap.clear();

    if (this._rafId) {
      cancelAnimationFrame(this._rafId);
      this._rafId = null;
    }
    this._pendingUpdate = null;
  }

  destroy() {
    this.clearAll();

    this.scene.primitives.remove(this.pointCollection);
    this.scene.primitives.remove(this.polylineCollection);
    this.scene.primitives.remove(this.labelCollection);
    this.scene.primitives.remove(this.billboardCollection);

    if (this._rafId) {
      cancelAnimationFrame(this._rafId);
    }
  }

  getStats() {
    return {
      flightPoints: this.flightPointMap.size,
      flightTrails: this.flightTrailMap.size,
      flightLabels: this.flightLabelMap.size,
      waypointPoints: this.waypointPointMap.size,
      waypointLabels: this.waypointLabelMap.size,
      totalPrimitives: this.pointCollection.length + this.polylineCollection.length + this.labelCollection.length,
    };
  }
}
