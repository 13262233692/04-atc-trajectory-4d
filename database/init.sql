-- ========================================
-- ATC Trajectory 4D Database Initialization
-- ========================================

-- Create database
CREATE DATABASE atc_trajectory
    WITH
    OWNER = postgres
    ENCODING = 'UTF8'
    LC_COLLATE = 'en_US.UTF-8'
    LC_CTYPE = 'en_US.UTF-8'
    TABLESPACE = pg_default
    CONNECTION LIMIT = -1;

-- Connect to the database
\c atc_trajectory;

-- Create user
CREATE USER atc WITH PASSWORD 'atc123';

-- Grant privileges
GRANT ALL PRIVILEGES ON DATABASE atc_trajectory TO atc;
GRANT ALL PRIVILEGES ON SCHEMA public TO atc;
GRANT ALL PRIVILEGES ON ALL TABLES IN SCHEMA public TO atc;
GRANT ALL PRIVILEGES ON ALL SEQUENCES IN SCHEMA public TO atc;
ALTER DEFAULT PRIVILEGES IN SCHEMA public GRANT ALL ON TABLES TO atc;
ALTER DEFAULT PRIVILEGES IN SCHEMA public GRANT ALL ON SEQUENCES TO atc;

-- ========================================
-- Table: flight_plans
-- ========================================
CREATE TABLE IF NOT EXISTS flight_plans (
    id BIGSERIAL PRIMARY KEY,
    flight_id VARCHAR(50) UNIQUE NOT NULL,
    aircraft_type VARCHAR(50) NOT NULL,
    airline VARCHAR(100),
    flight_number VARCHAR(20),
    departure_airport VARCHAR(10) NOT NULL,
    arrival_airport VARCHAR(10) NOT NULL,
    departure_time TIMESTAMP NOT NULL,
    estimated_arrival_time TIMESTAMP,
    actual_departure_time TIMESTAMP,
    actual_arrival_time TIMESTAMP,
    initial_mass DOUBLE PRECISION,
    fuel_mass DOUBLE PRECISION,
    payload_mass DOUBLE PRECISION,
    cruise_altitude DOUBLE PRECISION,
    cruise_speed DOUBLE PRECISION,
    cruise_mach DOUBLE PRECISION,
    status VARCHAR(20),
    created_at TIMESTAMP DEFAULT CURRENT_TIMESTAMP,
    updated_at TIMESTAMP DEFAULT CURRENT_TIMESTAMP
);

CREATE INDEX IF NOT EXISTS idx_flight_plans_flight_id ON flight_plans(flight_id);
CREATE INDEX IF NOT EXISTS idx_flight_plans_status ON flight_plans(status);
CREATE INDEX IF NOT EXISTS idx_flight_plans_departure_time ON flight_plans(departure_time);
CREATE INDEX IF NOT EXISTS idx_flight_plans_route ON flight_plans(departure_airport, arrival_airport);

-- ========================================
-- Table: waypoints
-- ========================================
CREATE TABLE IF NOT EXISTS waypoints (
    id BIGSERIAL PRIMARY KEY,
    flight_plan_id BIGINT NOT NULL REFERENCES flight_plans(id) ON DELETE CASCADE,
    sequence_number INTEGER NOT NULL,
    name VARCHAR(50) NOT NULL,
    latitude DOUBLE PRECISION NOT NULL,
    longitude DOUBLE PRECISION NOT NULL,
    altitude DOUBLE PRECISION,
    speed DOUBLE PRECISION,
    estimated_time DOUBLE PRECISION,
    is_departure BOOLEAN DEFAULT FALSE,
    is_destination BOOLEAN DEFAULT FALSE,
    created_at TIMESTAMP DEFAULT CURRENT_TIMESTAMP
);

CREATE INDEX IF NOT EXISTS idx_waypoints_flight_plan_id ON waypoints(flight_plan_id);
CREATE INDEX IF NOT EXISTS idx_waypoints_sequence ON waypoints(flight_plan_id, sequence_number);
CREATE INDEX IF NOT EXISTS idx_waypoints_name ON waypoints(name);

-- ========================================
-- Table: trajectory_points
-- ========================================
CREATE TABLE IF NOT EXISTS trajectory_points (
    id BIGSERIAL PRIMARY KEY,
    flight_plan_id BIGINT NOT NULL REFERENCES flight_plans(id) ON DELETE CASCADE,
    flight_id VARCHAR(50),
    sequence_number BIGINT NOT NULL,
    longitude DOUBLE PRECISION NOT NULL,
    latitude DOUBLE PRECISION NOT NULL,
    altitude DOUBLE PRECISION NOT NULL,
    timestamp TIMESTAMP NOT NULL,
    true_airspeed DOUBLE PRECISION,
    ground_speed DOUBLE PRECISION,
    mach_number DOUBLE PRECISION,
    heading DOUBLE PRECISION,
    track_angle DOUBLE PRECISION,
    vertical_speed DOUBLE PRECISION,
    mass DOUBLE PRECISION,
    fuel_mass DOUBLE PRECISION,
    thrust DOUBLE PRECISION,
    drag DOUBLE PRECISION,
    lift DOUBLE PRECISION,
    fuel_flow DOUBLE PRECISION,
    specific_range DOUBLE PRECISION,
    wind_speed DOUBLE PRECISION,
    wind_direction DOUBLE PRECISION,
    temperature DOUBLE PRECISION,
    flight_phase VARCHAR(20),
    next_waypoint VARCHAR(50),
    distance_to_destination DOUBLE PRECISION,
    time_to_destination DOUBLE PRECISION,
    created_at TIMESTAMP DEFAULT CURRENT_TIMESTAMP
);

CREATE INDEX IF NOT EXISTS idx_trajectory_points_flight_id ON trajectory_points(flight_plan_id);
CREATE INDEX IF NOT EXISTS idx_trajectory_points_sequence ON trajectory_points(flight_plan_id, sequence_number);
CREATE INDEX IF NOT EXISTS idx_trajectory_points_timestamp ON trajectory_points(timestamp);
CREATE INDEX IF NOT EXISTS idx_trajectory_points_position ON trajectory_points(latitude, longitude, altitude);
CREATE INDEX IF NOT EXISTS idx_trajectory_points_flight_phase ON trajectory_points(flight_phase);

-- ========================================
-- Table: aircraft_performance
-- ========================================
CREATE TABLE IF NOT EXISTS aircraft_performance (
    id BIGSERIAL PRIMARY KEY,
    aircraft_type VARCHAR(50) UNIQUE NOT NULL,
    engine_type VARCHAR(50),
    number_of_engines INTEGER,
    reference_mass DOUBLE PRECISION,
    max_takeoff_mass DOUBLE PRECISION,
    max_landing_mass DOUBLE PRECISION,
    max_zero_fuel_mass DOUBLE PRECISION,
    operating_empty_mass DOUBLE PRECISION,
    wing_area DOUBLE PRECISION,
    wing_span DOUBLE PRECISION,
    wing_aspect_ratio DOUBLE PRECISION,
    max_operating_mach DOUBLE PRECISION,
    max_operating_speed DOUBLE PRECISION,
    cruise_mach DOUBLE PRECISION,
    cruise_speed DOUBLE PRECISION,
    service_ceiling DOUBLE PRECISION,
    max_altitude DOUBLE PRECISION,
    stall_speed_landing DOUBLE PRECISION,
    stall_speed_takeoff DOUBLE PRECISION,
    cd0 DOUBLE PRECISION,
    cd2 DOUBLE PRECISION,
    cd4 DOUBLE PRECISION,
    k_factor DOUBLE PRECISION,
    mach_drag_index DOUBLE PRECISION,
    max_takeoff_thrust DOUBLE PRECISION,
    max_climb_thrust DOUBLE PRECISION,
    max_cruise_thrust DOUBLE PRECISION,
    thrust_altitude_factor DOUBLE PRECISION,
    thrust_mach_factor DOUBLE PRECISION,
    thrust_temperature_factor DOUBLE PRECISION,
    cf1 DOUBLE PRECISION,
    cf2 DOUBLE PRECISION,
    cr1 DOUBLE PRECISION,
    cr2 DOUBLE PRECISION,
    cd1 DOUBLE PRECISION,
    cd2_coeff DOUBLE PRECISION,
    idle_fuel_flow DOUBLE PRECISION,
    fuel_density DOUBLE PRECISION,
    created_at TIMESTAMP DEFAULT CURRENT_TIMESTAMP,
    updated_at TIMESTAMP DEFAULT CURRENT_TIMESTAMP
);

-- ========================================
-- Table: weather_data
-- ========================================
CREATE TABLE IF NOT EXISTS weather_data (
    id BIGSERIAL PRIMARY KEY,
    latitude DOUBLE PRECISION NOT NULL,
    longitude DOUBLE PRECISION NOT NULL,
    altitude DOUBLE PRECISION NOT NULL,
    wind_speed_u DOUBLE PRECISION,
    wind_speed_v DOUBLE PRECISION,
    wind_speed DOUBLE PRECISION,
    wind_direction DOUBLE PRECISION,
    temperature DOUBLE PRECISION,
    pressure DOUBLE PRECISION,
    density DOUBLE PRECISION,
    valid_time TIMESTAMP,
    forecast_source VARCHAR(50),
    created_at TIMESTAMP DEFAULT CURRENT_TIMESTAMP
);

CREATE INDEX IF NOT EXISTS idx_weather_data_position ON weather_data(latitude, longitude, altitude);
CREATE INDEX IF NOT EXISTS idx_weather_data_valid_time ON weather_data(valid_time);

-- ========================================
-- View: v_flight_summary
-- ========================================
CREATE OR REPLACE VIEW v_flight_summary AS
SELECT
    fp.id,
    fp.flight_id,
    fp.airline,
    fp.flight_number,
    fp.aircraft_type,
    fp.departure_airport,
    fp.arrival_airport,
    fp.departure_time,
    fp.estimated_arrival_time,
    fp.status,
    COUNT(tp.id) as trajectory_point_count,
    MAX(tp.timestamp) as last_update_time
FROM flight_plans fp
LEFT JOIN trajectory_points tp ON fp.id = tp.flight_plan_id
GROUP BY fp.id, fp.flight_id, fp.airline, fp.flight_number, fp.aircraft_type,
         fp.departure_airport, fp.arrival_airport, fp.departure_time,
         fp.estimated_arrival_time, fp.status;

-- ========================================
-- Function: get_trajectory_points_in_area
-- ========================================
CREATE OR REPLACE FUNCTION get_trajectory_points_in_area(
    min_lat DOUBLE PRECISION,
    max_lat DOUBLE PRECISION,
    min_lon DOUBLE PRECISION,
    max_lon DOUBLE PRECISION,
    min_alt DOUBLE PRECISION,
    max_alt DOUBLE PRECISION,
    start_time TIMESTAMP,
    end_time TIMESTAMP
)
RETURNS SETOF trajectory_points AS $$
BEGIN
    RETURN QUERY
    SELECT *
    FROM trajectory_points
    WHERE latitude BETWEEN min_lat AND max_lat
      AND longitude BETWEEN min_lon AND max_lon
      AND altitude BETWEEN min_alt AND max_alt
      AND timestamp BETWEEN start_time AND end_time
    ORDER BY timestamp;
END;
$$ LANGUAGE plpgsql;

-- ========================================
-- Insert sample airport data
-- ========================================
CREATE TABLE IF NOT EXISTS airports (
    id VARCHAR(10) PRIMARY KEY,
    name VARCHAR(100),
    city VARCHAR(100),
    country VARCHAR(50),
    latitude DOUBLE PRECISION,
    longitude DOUBLE PRECISION,
    altitude DOUBLE PRECISION
);

INSERT INTO airports (id, name, city, country, latitude, longitude, altitude) VALUES
('ZBAA', 'Beijing Capital International Airport', 'Beijing', 'China', 40.0801, 116.5846, 35.0),
('ZBAD', 'Beijing Daxing International Airport', 'Beijing', 'China', 39.5092, 116.4107, 25.0),
('ZSPD', 'Shanghai Pudong International Airport', 'Shanghai', 'China', 31.1434, 121.8058, 4.0),
('ZSSS', 'Shanghai Hongqiao International Airport', 'Shanghai', 'China', 31.1979, 121.3363, 3.0),
('ZGGG', 'Guangzhou Baiyun International Airport', 'Guangzhou', 'China', 23.3924, 113.2988, 15.0),
('ZGSZ', 'Shenzhen Bao''an International Airport', 'Shenzhen', 'China', 22.6393, 113.8107, 4.0),
('ZUUU', 'Chengdu Shuangliu International Airport', 'Chengdu', 'China', 30.5785, 103.9471, 508.0),
('ZUCK', 'Chongqing Jiangbei International Airport', 'Chongqing', 'China', 29.7192, 106.6417, 416.0),
('ZSNB', 'Ningbo Lishe International Airport', 'Ningbo', 'China', 29.8267, 121.4619, 4.0),
('ZSQD', 'Qingdao Liuting International Airport', 'Qingdao', 'China', 36.2661, 120.3744, 11.0)
ON CONFLICT (id) DO NOTHING;

-- ========================================
-- Grant table permissions
-- ========================================
GRANT ALL PRIVILEGES ON ALL TABLES IN SCHEMA public TO atc;
GRANT ALL PRIVILEGES ON ALL SEQUENCES IN SCHEMA public TO atc;
GRANT EXECUTE ON ALL FUNCTIONS IN SCHEMA public TO atc;

-- ========================================
-- Database initialization complete
-- ========================================
COMMENT ON DATABASE atc_trajectory IS 'Air Traffic Control 4D Trajectory Prediction Database';
