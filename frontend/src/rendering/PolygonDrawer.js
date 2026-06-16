import * as Cesium from 'cesium';

export class PolygonDrawer {
  constructor(viewer, onPolygonComplete, onDrawingCancel) {
    this.viewer = viewer;
    this.onPolygonComplete = onPolygonComplete;
    this.onDrawingCancel = onDrawingCancel;
    this.positions = [];
    this.tempPointEntities = [];
    this.tempLineEntity = null;
    this.tempPolygonEntity = null;
    this.handler = null;
    this.active = false;
  }

  start() {
    if (this.active) return;
    this.active = true;
    this.positions = [];

    const scene = this.viewer.scene;
    const canvas = scene.canvas;
    canvas.style.cursor = 'crosshair';

    this.handler = new Cesium.ScreenSpaceEventHandler(canvas);

    this.handler.setInputAction((movement) => {
      const cartesian = this._pickOnTerrain(movement.position);
      if (!cartesian) return;
      this._addVertex(cartesian);
    }, Cesium.ScreenSpaceEventType.LEFT_CLICK);

    this.handler.setInputAction((movement) => {
      if (this.positions.length < 1) return;
      const cartesian = this._pickOnTerrain(movement.endPosition);
      if (!cartesian) return;
      this._updatePreview(cartesian);
    }, Cesium.ScreenSpaceEventType.MOUSE_MOVE);

    this.handler.setInputAction((movement) => {
      const cartesian = this._pickOnTerrain(movement.position);
      if (cartesian) this._addVertex(cartesian);
      this._finish();
    }, Cesium.ScreenSpaceEventType.LEFT_DOUBLE_CLICK);

    this.handler.setInputAction(() => {
      this.cancel();
    }, Cesium.ScreenSpaceEventType.RIGHT_CLICK);
  }

  _pickOnTerrain(windowPosition) {
    const viewer = this.viewer;
    const ray = viewer.camera.getPickRay(windowPosition);
    if (!ray) return null;
    const cartesian = viewer.scene.globe.pick(ray, viewer.scene);
    if (!cartesian) {
      return viewer.camera.pickEllipsoid(windowPosition, viewer.scene.globe.ellipsoid);
    }
    return cartesian;
  }

  static cartesianToLonLatAlt(cartesian) {
    const cartographic = Cesium.Cartographic.fromCartesian(cartesian);
    return {
      longitude: Cesium.Math.toDegrees(cartographic.longitude),
      latitude: Cesium.Math.toDegrees(cartographic.latitude),
      altitude: cartographic.height,
    };
  }

  _addVertex(cartesian) {
    this.positions.push(cartesian);
    const point = this.viewer.entities.add({
      position: cartesian,
      point: {
        pixelSize: 8,
        color: Cesium.Color.RED.withAlpha(0.9),
        outlineColor: Cesium.Color.WHITE,
        outlineWidth: 2,
        disableDepthTestDistance: Number.POSITIVE_INFINITY,
      },
    });
    this.tempPointEntities.push(point);
    this._updatePreview(cartesian);
  }

  _updatePreview(mouseCartesian) {
    if (this.positions.length === 0) return;

    const previewPositions = [...this.positions, mouseCartesian];

    if (!this.tempLineEntity) {
      this.tempLineEntity = this.viewer.entities.add({
        polyline: {
          positions: previewPositions,
          width: 2,
          material: Cesium.Color.YELLOW.withAlpha(0.7),
          clampToGround: false,
        },
      });
    } else {
      this.tempLineEntity.polyline.positions = new Cesium.CallbackProperty(
        () => previewPositions, false
      );
    }

    if (this.positions.length >= 2) {
      const polyPositions = [...this.positions, mouseCartesian, this.positions[0]];
      if (!this.tempPolygonEntity) {
        this.tempPolygonEntity = this.viewer.entities.add({
          polygon: {
            hierarchy: new Cesium.CallbackProperty(() => polyPositions, false),
            material: Cesium.Color.RED.withAlpha(0.15),
            outline: true,
            outlineColor: Cesium.Color.RED.withAlpha(0.8),
            outlineWidth: 2,
            perPositionHeight: true,
          },
        });
      } else {
        this.tempPolygonEntity.polygon.hierarchy = new Cesium.CallbackProperty(
          () => new Cesium.PolygonHierarchy(polyPositions), false
        );
      }
    }
  }

  _finish() {
    if (this.positions.length < 3) {
      this.cancel();
      return;
    }
    const vertices = this.positions.map(PolygonDrawer.cartesianToLonLatAlt);
    this._cleanupTempEntities();
    this.active = false;
    this.viewer.scene.canvas.style.cursor = '';
    if (this.handler) {
      this.handler.destroy();
      this.handler = null;
    }
    if (this.onPolygonComplete) {
      this.onPolygonComplete(vertices);
    }
  }

  cancel() {
    this._cleanupTempEntities();
    this.positions = [];
    this.active = false;
    if (this.viewer && this.viewer.scene) {
      this.viewer.scene.canvas.style.cursor = '';
    }
    if (this.handler) {
      this.handler.destroy();
      this.handler = null;
    }
    if (this.onDrawingCancel) {
      this.onDrawingCancel();
    }
  }

  _cleanupTempEntities() {
    const viewer = this.viewer;
    if (!viewer) return;
    this.tempPointEntities.forEach(p => viewer.entities.remove(p));
    this.tempPointEntities = [];
    if (this.tempLineEntity) {
      viewer.entities.remove(this.tempLineEntity);
      this.tempLineEntity = null;
    }
    if (this.tempPolygonEntity) {
      viewer.entities.remove(this.tempPolygonEntity);
      this.tempPolygonEntity = null;
    }
  }

  destroy() {
    this.cancel();
  }
}
