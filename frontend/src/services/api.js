import axios from 'axios';

const API_BASE_URL = import.meta.env.VITE_API_BASE_URL || 'http://localhost:8080/api';

const api = axios.create({
  baseURL: API_BASE_URL,
  timeout: 30000,
  headers: {
    'Content-Type': 'application/json',
  },
});

api.interceptors.response.use(
  (response) => response.data,
  (error) => {
    console.error('API Error:', error);
    return Promise.reject(error);
  }
);

export const fetchAllFlights = async () => {
  try {
    const response = await api.get('/trajectory/flights');
    if (response && response.data) {
      return response.data;
    }
    return [];
  } catch (error) {
    console.error('Error fetching flights:', error);
    return [
      {
        flightId: 'MU5101',
        aircraftType: 'A320',
        airline: 'China Eastern',
        departureAirport: 'ZBAA',
        arrivalAirport: 'ZSPD',
        status: 'SCHEDULED',
      },
      {
        flightId: 'CA1352',
        aircraftType: 'B737-800',
        airline: 'Air China',
        departureAirport: 'ZGGG',
        arrivalAirport: 'ZBAD',
        status: 'SCHEDULED',
      },
    ];
  }
};

export const fetchFlight = async (flightId) => {
  return api.get(`/trajectory/${flightId}`);
};

export const calculateTrajectory = async (flightId) => {
  return api.post(`/trajectory/calculate/${flightId}`);
};

export const startStreaming = async (flightId) => {
  return api.post(`/trajectory/stream/start/${flightId}`);
};

export const stopStreaming = async (flightId) => {
  return api.post(`/trajectory/stream/stop/${flightId}`);
};

export const fetchTrajectoryPoints = async (flightId) => {
  return api.get(`/trajectory/${flightId}/points`);
};

export const fetchBadaAircraft = async () => {
  return api.get('/bada/aircraft');
};

export const fetchBadaPerformance = async (aircraftType) => {
  return api.get(`/bada/performance/${aircraftType}`);
};

export const uploadBadaFile = async (file) => {
  const formData = new FormData();
  formData.append('file', file);
  return api.post('/bada/upload', formData, {
    headers: {
      'Content-Type': 'multipart/form-data',
    },
  });
};

export const fetchWeatherData = async (lat, lon, alt, time) => {
  return api.get('/weather/interpolate', {
    params: { lat, lon, alt, time },
  });
};

export const fetchWeatherGrid = async () => {
  return api.get('/weather/grid');
};

export const generateWeatherData = async () => {
  return api.post('/weather/generate');
};

export const fetchAllAirspaces = async () => {
  try {
    const response = await api.get('/airspace');
    return response?.data || [];
  } catch (e) {
    console.error('Error fetching airspaces:', e);
    return [];
  }
};

export const createAirspace = async (payload) => {
  const response = await api.post('/airspace', payload);
  return response?.data;
};

export const updateAirspace = async (airspaceId, payload) => {
  const response = await api.put(`/airspace/${airspaceId}`, payload);
  return response?.data;
};

export const deleteAirspace = async (airspaceId) => {
  return api.delete(`/airspace/${airspaceId}`);
};

export const triggerAirspaceAvoidance = async (airspaceId) => {
  return api.post(`/airspace/trigger/${airspaceId}`);
};

export const rerouteFlight = async (flightId) => {
  const response = await api.post(`/airspace/reroute/${flightId}`);
  return response?.data;
};

export default api;
