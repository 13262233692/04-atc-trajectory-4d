class RingBuffer {
  constructor(capacity) {
    this.capacity = capacity;
    this.buffer = new Array(capacity);
    this.head = 0;
    this.tail = 0;
    this.size = 0;
  }
  push(item) {
    this.buffer[this.tail] = item;
    this.tail = (this.tail + 1) % this.capacity;
    if (this.size === this.capacity) {
      this.head = (this.head + 1) % this.capacity;
    } else {
      this.size++;
    }
  }
  get(index) {
    if (index < 0 || index >= this.size) return undefined;
    return this.buffer[(this.head + index) % this.capacity];
  }
  getLatest() {
    if (this.size === 0) return undefined;
    return this.buffer[(this.tail - 1 + this.capacity) % this.capacity];
  }
  getArray() {
    const result = [];
    for (let i = 0; i < this.size; i++) {
      result.push(this.buffer[(this.head + i) % this.capacity]);
    }
    return result;
  }
  clear() {
    this.head = 0;
    this.tail = 0;
    this.size = 0;
  }
  get length() { return this.size; }
}

class FlightTrajectoryBuffer {
  constructor(flightId, maxPositions = 720, maxHistoryPoints = 120) {
    this.flightId = flightId;
    this.positions = new RingBuffer(maxPositions);
    this.historyPoints = new RingBuffer(maxHistoryPoints);
    this.currentPoint = null;
    this.currentPosition = null;
    this.flightPhase = null;
    this.lastUpdateTime = 0;
    this.visible = true;
  }
  update(point, position) {
    this.currentPoint = point;
    this.currentPosition = position;
    this.flightPhase = point.flightPhase || null;
    this.lastUpdateTime = Date.now();
    this.positions.push(position);
    this.historyPoints.push(point);
  }
  getTrailPositions(count = 60) {
    const result = [];
    const start = Math.max(0, this.positions.size - count);
    for (let i = start; i < this.positions.size; i++) {
      result.push(this.positions.get(i - start));
    }
    return result;
  }
  getHistoryArray() { return this.historyPoints.getArray(); }
  clear() {
    this.positions.clear();
    this.historyPoints.clear();
    this.currentPoint = null;
    this.currentPosition = null;
    this.flightPhase = null;
  }
}

const DEG2RAD = Math.PI / 180;
const EARTH_RADIUS_X = 6378137.0;
const EARTH_RADIUS_Y = 6356752.3142451793;

const flights = new Map();
const MAX_FLIGHTS = 2000;
const POSITIONS_PER_FLIGHT = 720;

let frameBatch = [];
let batchTimer = null;
const BATCH_INTERVAL = 100;

function lonLatAltToCartesian(lon, lat, alt) {
  const lonRad = lon * DEG2RAD;
  const latRad = lat * DEG2RAD;
  const cosLat = Math.cos(latRad);
  const sinLat = Math.sin(latRad);
  const cosLon = Math.cos(lonRad);
  const sinLon = Math.sin(lonRad);
  const n = EARTH_RADIUS_X / Math.sqrt(1 - ((EARTH_RADIUS_X * EARTH_RADIUS_X - EARTH_RADIUS_Y * EARTH_RADIUS_Y) / (EARTH_RADIUS_X * EARTH_RADIUS_X)) * sinLat * sinLat);
  const rn = n + alt;
  const x = rn * cosLat * cosLon;
  const y = rn * cosLat * sinLon;
  const z = ((EARTH_RADIUS_Y * EARTH_RADIUS_Y / (EARTH_RADIUS_X * EARTH_RADIUS_X)) * n + alt) * sinLat;
  return { x, y, z };
}

function getFlightPhaseColorIndex(phase) {
  switch (phase) {
    case 'TAKEOFF': return 0;
    case 'CLIMB': return 1;
    case 'CRUISE': return 2;
    case 'DESCENT': return 3;
    case 'LANDING': return 4;
    default: return 5;
  }
}

function processTrajectoryPoint(data) {
  const { flightId, longitude, latitude, altitude, ...rest } = data;

  if (!flightId || longitude == null || latitude == null || altitude == null) {
    return null;
  }

  let buffer = flights.get(flightId);
  if (!buffer) {
    if (flights.size >= MAX_FLIGHTS) {
      let oldestId = null;
      let oldestTime = Infinity;
      for (const [id, buf] of flights) {
        if (buf.lastUpdateTime < oldestTime) {
          oldestTime = buf.lastUpdateTime;
          oldestId = id;
        }
      }
      if (oldestId) {
        flights.delete(oldestId);
      }
    }
    buffer = new FlightTrajectoryBuffer(flightId, POSITIONS_PER_FLIGHT, 120);
    flights.set(flightId, buffer);
  }

  const position = lonLatAltToCartesian(longitude, latitude, Math.max(0, altitude));
  buffer.update(data, position);

  return {
    flightId,
    position,
    flightPhase: data.flightPhase,
    colorIndex: getFlightPhaseColorIndex(data.flightPhase),
    altitude: data.altitude,
    groundSpeed: data.groundSpeed,
    heading: data.heading,
    verticalSpeed: data.verticalSpeed,
    timestamp: data.timestamp,
  };
}

function flushBatch() {
  if (frameBatch.length === 0) return;

  const updates = frameBatch;
  frameBatch = [];

  const flightPositions = [];
  const flightTrails = [];
  const flightPhases = [];

  for (const update of updates) {
    if (!update) continue;
    flightPositions.push({
      flightId: update.flightId,
      x: update.position.x,
      y: update.position.y,
      z: update.position.z,
      colorIndex: update.colorIndex,
    });

    const buffer = flights.get(update.flightId);
    if (buffer) {
      const trail = buffer.getTrailPositions(60);
      if (trail.length >= 2) {
        const trailPositions = new Float64Array(trail.length * 3);
        for (let i = 0; i < trail.length; i++) {
          trailPositions[i * 3] = trail[i].x;
          trailPositions[i * 3 + 1] = trail[i].y;
          trailPositions[i * 3 + 2] = trail[i].z;
        }
        flightTrails.push({
          flightId: update.flightId,
          positions: trailPositions,
          colorIndex: update.colorIndex,
        });
      }
    }
  }

  const selectedFlightId = self._selectedFlightId;
  let selectedPoint = null;
  if (selectedFlightId) {
    const buf = flights.get(selectedFlightId);
    if (buf && buf.currentPoint) {
      selectedPoint = buf.currentPoint;
    }
  }

  self.postMessage({
    type: 'frame-update',
    flightPositions,
    flightTrails,
    selectedPoint,
    stats: {
      totalFlights: flights.size,
      batchSize: updates.length,
    },
  }, flightTrails.map(t => t.positions.buffer).filter(b => b instanceof ArrayBuffer));
}

function onMessage(event) {
  const { type, data } = event.data;

  switch (type) {
    case 'trajectory-point': {
      const result = processTrajectoryPoint(data);
      if (result) {
        frameBatch.push(result);
        if (!batchTimer) {
          batchTimer = setTimeout(() => {
            batchTimer = null;
            flushBatch();
          }, BATCH_INTERVAL);
        }
      }
      break;
    }

    case 'trajectory-batch': {
      const points = data;
      for (let i = 0; i < points.length; i++) {
        const result = processTrajectoryPoint(points[i]);
        if (result) {
          frameBatch.push(result);
        }
      }
      if (!batchTimer) {
        batchTimer = setTimeout(() => {
          batchTimer = null;
          flushBatch();
        }, BATCH_INTERVAL);
      }
      break;
    }

    case 'select-flight': {
      self._selectedFlightId = data.flightId;
      const buf = flights.get(data.flightId);
      self.postMessage({
        type: 'flight-selected',
        flightId: data.flightId,
        historyPoints: buf ? buf.getHistoryArray() : [],
        currentPoint: buf ? buf.currentPoint : null,
      });
      break;
    }

    case 'clear-flight': {
      const id = data.flightId;
      const buf = flights.get(id);
      if (buf) {
        buf.clear();
      }
      break;
    }

    case 'clear-all': {
      flights.clear();
      frameBatch = [];
      if (batchTimer) {
        clearTimeout(batchTimer);
        batchTimer = null;
      }
      self.postMessage({ type: 'cleared' });
      break;
    }

    case 'get-stats': {
      self.postMessage({
        type: 'stats',
        data: {
          totalFlights: flights.size,
          poolSize: 0,
        },
      });
      break;
    }

    case 'frustum-cull': {
      const { planes, cameraPosition } = data;
      const visibleFlights = [];

      for (const [flightId, buffer] of flights) {
        if (!buffer.currentPosition) continue;

        const pos = buffer.currentPosition;
        let insideFrustum = true;

        for (let i = 0; i < 6; i++) {
          const plane = planes[i];
          const dist = plane.x * pos.x + plane.y * pos.y + plane.z * pos.z + plane.w;
          const radius = 50000;
          if (dist < -radius) {
            insideFrustum = false;
            break;
          }
        }

        buffer.visible = insideFrustum;
        if (insideFrustum) {
          visibleFlights.push({
            flightId,
            x: pos.x,
            y: pos.y,
            z: pos.z,
            colorIndex: getFlightPhaseColorIndex(buffer.flightPhase),
          });
        }
      }

      self.postMessage({
        type: 'frustum-result',
        visibleFlights,
        cameraPosition,
      });
      break;
    }
  }
}

self.onmessage = onMessage;
self._selectedFlightId = null;

self.postMessage({ type: 'worker-ready' });
