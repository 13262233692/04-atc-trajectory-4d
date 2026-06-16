export class RingBuffer {
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

  get length() {
    return this.size;
  }

  isFull() {
    return this.size === this.capacity;
  }

  isEmpty() {
    return this.size === 0;
  }
}

export class FlightTrajectoryBuffer {
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

  getPositionArray() {
    return this.positions.getArray();
  }

  getTrailPositions(count = 60) {
    const result = [];
    const start = Math.max(0, this.positions.size - count);
    for (let i = start; i < this.positions.size; i++) {
      result.push(this.positions.get(i - start));
    }
    return result;
  }

  getHistoryArray() {
    return this.historyPoints.getArray();
  }

  clear() {
    this.positions.clear();
    this.historyPoints.clear();
    this.currentPoint = null;
    this.currentPosition = null;
    this.flightPhase = null;
  }
}
