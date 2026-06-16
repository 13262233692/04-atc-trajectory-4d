import * as Cesium from 'cesium';

const LEVEL_COLORS = {
  PROHIBITED: { fill: Cesium.Color.RED, outline: Cesium.Color.RED, alpha: 0.18 },
  WARNING: { fill: Cesium.Color.ORANGERED, outline: Cesium.Color.ORANGERED, alpha: 0.15 },
  ADVISORY: { fill: Cesium.Color.ORANGE, outline: Cesium.Color.ORANGE, alpha: 0.12 },
};

export class RestrictedAirspaceRenderer {
  constructor(viewer) {
    this.viewer = viewer;
    this.airspaceEntities = new Map();
    this.routeComparisonEntities = [];
  }

  renderAirspace(airspace) {
    this.removeAirspace(airspace.id);

    if (!airspace.polygonVertices || airspace.polygonVertices.length < 3) return;

    const positions = airspace.polygonVertices.map(v =>
      Cesium.Cartesian3.fromDegrees(v.longitude, v.latitude,
        airspace.maxAltitude != null ? airspace.maxAltitude : 12000)
    );

    const cfg = LEVEL_COLORS[airspace.level] || LEVEL_COLORS.PROHIBITED;

    const label = [airspace.name || airspace.id];
    if (airspace.reason) label.push(airspace.reason);
    const labelText = label.join(' — ');

    const centroid = airspace.polygonVertices.reduce(
      (acc, v) => ({ lon: acc.lon + v.longitude, lat: acc.lat + v.latitude }),
      { lon: 0, lat: 0 }
    );
    centroid.lon /= airspace.polygonVertices.length;
    centroid.lat /= airspace.polygonVertices.length;

    const entity = this.viewer.entities.add({
      id: `airspace-${airspace.id}`,
      name: labelText,
      polygon: {
        hierarchy: positions,
        material: cfg.fill.withAlpha(airspace.active ? cfg.alpha : 0.08),
        outline: true,
        outlineColor: cfg.outline.withAlpha(airspace.active ? 0.9 : 0.4),
        outlineWidth: 2,
        perPositionHeight: true,
      },
      polyline: {
        positions: [...positions, positions[0]],
        width: 2,
        material: cfg.outline.withAlpha(airspace.active ? 0.85 : 0.4),
        clampToGround: false,
      },
      wall: {
        positions: [...positions, positions[0]],
        maximumHeights: positions.map(() => airspace.maxAltitude || 12000),
        minimumHeights: positions.map(() => airspace.minAltitude || 0),
        material: cfg.fill.withAlpha(airspace.active ? cfg.alpha * 0.7 : 0.05),
        outline: true,
        outlineColor: cfg.outline.withAlpha(airspace.active ? 0.6 : 0.3),
      },
      position: Cesium.Cartesian3.fromDegrees(centroid.lon, centroid.lat, (airspace.maxAltitude || 12000) + 300),
      label: {
        text: labelText,
        font: 'bold 13px sans-serif',
        fillColor: airspace.active ? Cesium.Color.WHITE : Cesium.Color.GRAY,
        outlineColor: Cesium.Color.BLACK,
        outlineWidth: 3,
        style: Cesium.LabelStyle.FILL_AND_OUTLINE,
        pixelOffset: new Cesium.Cartesian2(0, -14),
        disableDepthTestDistance: Number.POSITIVE_INFINITY,
        showBackground: true,
        backgroundColor: cfg.fill.withAlpha(0.5),
        backgroundPadding: new Cesium.Cartesian2(8, 4),
      },
    });

    this.airspaceEntities.set(airspace.id, entity);
  }

  removeAirspace(airspaceId) {
    const entity = this.airspaceEntities.get(airspaceId);
    if (entity) {
      this.viewer.entities.remove(entity);
      this.airspaceEntities.delete(airspaceId);
    }
  }

  clearAllAirspaces() {
    for (const id of this.airspaceEntities.keys()) {
      this.removeAirspace(id);
    }
  }

  renderRouteComparison(originalWaypoints, detourWaypoints) {
    this.clearRouteComparison();

    if (originalWaypoints && originalWaypoints.length > 1) {
      const positions = originalWaypoints.map(w =>
        Cesium.Cartesian3.fromDegrees(w.longitude, w.latitude, w.altitude || 10000)
      );
      const e = this.viewer.entities.add({
        polyline: {
          positions,
          width: 4,
          material: new Cesium.PolylineDashMaterialProperty({
            color: Cesium.Color.RED.withAlpha(0.85),
            dashLength: 16,
          }),
          clampToGround: false,
        },
      });
      this.routeComparisonEntities.push(e);
    }

    if (detourWaypoints && detourWaypoints.length > 1) {
      const positions = detourWaypoints.map(w =>
        Cesium.Cartesian3.fromDegrees(w.longitude, w.latitude, w.altitude || 10000)
      );
      const e = this.viewer.entities.add({
        polyline: {
          positions,
          width: 4,
          material: Cesium.Color.LIME.withAlpha(0.9),
          clampToGround: false,
        },
      });
      this.routeComparisonEntities.push(e);

      detourWaypoints.forEach((wp, i) => {
        const pe = this.viewer.entities.add({
          position: Cesium.Cartesian3.fromDegrees(wp.longitude, wp.latitude, wp.altitude || 10000),
          point: {
            pixelSize: 9,
            color: Cesium.Color.LIME,
            outlineColor: Cesium.Color.WHITE,
            outlineWidth: 2,
            disableDepthTestDistance: Number.POSITIVE_INFINITY,
          },
          label: {
            text: wp.name || `W${i}`,
            font: '10px monospace',
            fillColor: Cesium.Color.WHITE,
            outlineColor: Cesium.Color.BLACK,
            outlineWidth: 2,
            style: Cesium.LabelStyle.FILL_AND_OUTLINE,
            pixelOffset: new Cesium.Cartesian2(0, -14),
            disableDepthTestDistance: Number.POSITIVE_INFINITY,
          },
        });
        this.routeComparisonEntities.push(pe);
      });
    }
  }

  clearRouteComparison() {
    this.routeComparisonEntities.forEach(e => this.viewer.entities.remove(e));
    this.routeComparisonEntities = [];
  }

  destroy() {
    this.clearAllAirspaces();
    this.clearRouteComparison();
  }
}
