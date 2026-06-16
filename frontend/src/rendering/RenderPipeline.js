export class RenderPipeline {
  constructor() {
    this.worker = null;
    this.renderer = null;
    this.viewer = null;
    this.onFrameUpdate = null;
    this.onFlightSelected = null;
    this.onCleared = null;
    this._workerReady = false;
    this._cullingEnabled = true;
    this._lastCullTime = 0;
    this._cullInterval = 1000;
    this._statsCallback = null;
  }

  init(viewer, onFrameUpdate, onFlightSelected, onCleared) {
    this.viewer = viewer;
    this.onFrameUpdate = onFrameUpdate;
    this.onFlightSelected = onFlightSelected;
    this.onCleared = onCleared;

    this.worker = new Worker(
      new URL('../workers/trajectoryWorker.js', import.meta.url),
      { type: 'module' }
    );

    this.worker.onmessage = (event) => {
      this._handleWorkerMessage(event.data);
    };

    this.worker.onerror = (error) => {
      console.error('Trajectory Worker error:', error);
    };

    this.viewer.scene.postRender.addEventListener(() => {
      this._onPostRender();
    });
  }

  _handleWorkerMessage(data) {
    switch (data.type) {
      case 'worker-ready':
        this._workerReady = true;
        console.log('Trajectory Worker ready');
        break;

      case 'frame-update':
        if (this.onFrameUpdate) {
          this.onFrameUpdate(data);
        }
        break;

      case 'flight-selected':
        if (this.onFlightSelected) {
          this.onFlightSelected(data);
        }
        break;

      case 'cleared':
        if (this.onCleared) {
          this.onCleared();
        }
        break;

      case 'frustum-result':
        break;

      case 'stats':
        if (this._statsCallback) {
          this._statsCallback(data.data);
          this._statsCallback = null;
        }
        break;
    }
  }

  _onPostRender() {
    if (!this._cullingEnabled || !this.viewer) return;

    const now = Date.now();
    if (now - this._lastCullTime < this._cullInterval) return;
    this._lastCullTime = now;

    if (this.renderer) {
      const stats = this.renderer.performFrustumCulling(this.viewer.camera);
    }
  }

  feedTrajectoryPoint(pointData) {
    if (!this._workerReady || !this.worker) return;
    this.worker.postMessage({
      type: 'trajectory-point',
      data: pointData,
    });
  }

  feedTrajectoryBatch(pointsArray) {
    if (!this._workerReady || !this.worker) return;
    this.worker.postMessage({
      type: 'trajectory-batch',
      data: pointsArray,
    });
  }

  selectFlight(flightId) {
    if (!this._workerReady || !this.worker) return;
    if (this.renderer) {
      this.renderer.setSelectedFlight(flightId);
    }
    this.worker.postMessage({
      type: 'select-flight',
      data: { flightId },
    });
  }

  clearFlight(flightId) {
    if (!this._workerReady || !this.worker) return;
    this.worker.postMessage({
      type: 'clear-flight',
      data: { flightId },
    });
  }

  clearAll() {
    if (!this._workerReady || !this.worker) return;
    if (this.renderer) {
      this.renderer.clearAll();
    }
    this.worker.postMessage({ type: 'clear-all' });
  }

  getStats() {
    return new Promise((resolve) => {
      if (!this._workerReady || !this.worker) {
        resolve({ totalFlights: 0, rendererStats: null });
        return;
      }
      this._statsCallback = (data) => {
        resolve({
          ...data,
          rendererStats: this.renderer ? this.renderer.getStats() : null,
        });
      };
      this.worker.postMessage({ type: 'get-stats' });
    });
  }

  setCullingEnabled(enabled) {
    this._cullingEnabled = enabled;
  }

  destroy() {
    if (this.worker) {
      this.worker.terminate();
      this.worker = null;
    }
    if (this.renderer) {
      this.renderer.destroy();
      this.renderer = null;
    }
    this._workerReady = false;
  }
}
