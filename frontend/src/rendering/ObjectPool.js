export class ObjectPool {
  constructor(factory, resetFn, initialSize = 0) {
    this.factory = factory;
    this.resetFn = resetFn;
    this.pool = [];
    this.activeCount = 0;

    for (let i = 0; i < initialSize; i++) {
      this.pool.push(factory());
    }
  }

  acquire() {
    let obj;
    if (this.pool.length > 0) {
      obj = this.pool.pop();
    } else {
      obj = this.factory();
    }
    this.activeCount++;
    return obj;
  }

  release(obj) {
    if (this.resetFn) {
      this.resetFn(obj);
    }
    this.pool.push(obj);
    this.activeCount--;
  }

  releaseAll(objects) {
    for (let i = 0; i < objects.length; i++) {
      this.release(objects[i]);
    }
  }

  prewarm(count) {
    for (let i = 0; i < count; i++) {
      this.pool.push(this.factory());
    }
  }

  get poolSize() {
    return this.pool.length;
  }
}

export class TrajectoryPointPool {
  constructor(initialSize = 5000) {
    this.pool = new ObjectPool(
      () => ({
        flightId: null,
        longitude: 0,
        latitude: 0,
        altitude: 0,
        timestamp: null,
        trueAirspeed: 0,
        groundSpeed: 0,
        machNumber: 0,
        heading: 0,
        trackAngle: 0,
        verticalSpeed: 0,
        mass: 0,
        fuelMass: 0,
        thrust: 0,
        drag: 0,
        lift: 0,
        fuelFlow: 0,
        specificRange: 0,
        windSpeed: 0,
        windDirection: 0,
        temperature: 0,
        flightPhase: null,
        nextWaypoint: null,
        distanceToDestination: 0,
        timeToDestination: 0,
      }),
      (obj) => {
        obj.flightId = null;
        obj.flightPhase = null;
        obj.nextWaypoint = null;
        obj.timestamp = null;
      },
      initialSize
    );
  }

  acquire(data) {
    const obj = this.pool.acquire();
    if (data) {
      Object.assign(obj, data);
    }
    return obj;
  }

  release(obj) {
    this.pool.release(obj);
  }
}

export class PositionPool {
  constructor(initialSize = 10000) {
    this.pool = new ObjectPool(
      () => ({ x: 0, y: 0, z: 0 }),
      (obj) => { obj.x = 0; obj.y = 0; obj.z = 0; },
      initialSize
    );
  }

  acquire(x, y, z) {
    const obj = this.pool.acquire();
    obj.x = x;
    obj.y = y;
    obj.z = z;
    return obj;
  }

  release(obj) {
    this.pool.release(obj);
  }
}
