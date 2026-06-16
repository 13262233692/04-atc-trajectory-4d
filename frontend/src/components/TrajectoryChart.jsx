import { useMemo } from 'react';
import { LineChart, Line, XAxis, YAxis, CartesianGrid, Tooltip, ResponsiveContainer, Area, AreaChart } from 'recharts';
import { format } from 'date-fns';

function TrajectoryChart({ trajectoryPoints }) {
  const chartData = useMemo(() => {
    if (!trajectoryPoints || trajectoryPoints.length === 0) {
      return [];
    }

    const step = Math.max(1, Math.floor(trajectoryPoints.length / 50));

    return trajectoryPoints
      .filter((_, index) => index % step === 0)
      .map((point, index) => ({
        index: index * step,
        time: point.timestamp ? format(new Date(point.timestamp), 'HH:mm:ss') : `T${index * step * 5}s`,
        altitude: point.altitude ? Math.round(point.altitude) : 0,
        groundSpeed: point.groundSpeed ? Math.round(point.groundSpeed) : 0,
        machNumber: point.machNumber ? point.machNumber.toFixed(2) : 0,
        fuelFlow: point.fuelFlow ? point.fuelFlow.toFixed(2) : 0,
        fuelMass: point.fuelMass ? Math.round(point.fuelMass) : 0,
        verticalSpeed: point.verticalSpeed ? point.verticalSpeed.toFixed(1) : 0,
      }));
  }, [trajectoryPoints]);

  const CustomTooltip = ({ active, payload, label }) => {
    if (active && payload && payload.length) {
      return (
        <div className="bg-atc-panel border border-white/20 rounded-lg p-3 shadow-xl">
          <p className="text-xs text-gray-400 mb-2">{label}</p>
          {payload.map((entry, index) => (
            <p key={index} className="text-sm" style={{ color: entry.color }}>
              {entry.name}: {entry.value} {entry.unit || ''}
            </p>
          ))}
        </div>
      );
    }
    return null;
  };

  if (chartData.length < 2) {
    return (
      <div className="h-40 flex items-center justify-center text-gray-400 text-sm">
        Insufficient data for chart
      </div>
    );
  }

  return (
    <div className="space-y-4">
      {/* Altitude Profile */}
      <div>
        <h4 className="text-xs text-gray-400 mb-2">Altitude Profile</h4>
        <div className="h-32">
          <ResponsiveContainer width="100%" height="100%">
            <AreaChart data={chartData}>
              <defs>
                <linearGradient id="altitudeGradient" x1="0" y1="0" x2="0" y2="1">
                  <stop offset="5%" stopColor="#0ea5e9" stopOpacity={0.8} />
                  <stop offset="95%" stopColor="#0ea5e9" stopOpacity={0.1} />
                </linearGradient>
              </defs>
              <CartesianGrid strokeDasharray="3 3" stroke="rgba(255,255,255,0.1)" />
              <XAxis
                dataKey="time"
                tick={{ fill: '#94a3b8', fontSize: 10 }}
                axisLine={{ stroke: 'rgba(255,255,255,0.2)' }}
                tickLine={false}
                interval="preserveStartEnd"
              />
              <YAxis
                tick={{ fill: '#94a3b8', fontSize: 10 }}
                axisLine={{ stroke: 'rgba(255,255,255,0.2)' }}
                tickLine={false}
                width={50}
              />
              <Tooltip content={<CustomTooltip />} />
              <Area
                type="monotone"
                dataKey="altitude"
                stroke="#0ea5e9"
                fill="url(#altitudeGradient)"
                name="Altitude"
                unit="m"
              />
            </AreaChart>
          </ResponsiveContainer>
        </div>
      </div>

      {/* Speed Profile */}
      <div>
        <h4 className="text-xs text-gray-400 mb-2">Ground Speed</h4>
        <div className="h-24">
          <ResponsiveContainer width="100%" height="100%">
            <LineChart data={chartData}>
              <CartesianGrid strokeDasharray="3 3" stroke="rgba(255,255,255,0.1)" />
              <XAxis
                dataKey="time"
                tick={{ fill: '#94a3b8', fontSize: 10 }}
                axisLine={{ stroke: 'rgba(255,255,255,0.2)' }}
                tickLine={false}
                interval="preserveStartEnd"
              />
              <YAxis
                tick={{ fill: '#94a3b8', fontSize: 10 }}
                axisLine={{ stroke: 'rgba(255,255,255,0.2)' }}
                tickLine={false}
                width={50}
              />
              <Tooltip content={<CustomTooltip />} />
              <Line
                type="monotone"
                dataKey="groundSpeed"
                stroke="#10b981"
                strokeWidth={2}
                dot={false}
                name="Speed"
                unit="m/s"
              />
            </LineChart>
          </ResponsiveContainer>
        </div>
      </div>

      {/* Fuel Flow */}
      <div>
        <h4 className="text-xs text-gray-400 mb-2">Fuel Flow</h4>
        <div className="h-24">
          <ResponsiveContainer width="100%" height="100%">
            <LineChart data={chartData}>
              <CartesianGrid strokeDasharray="3 3" stroke="rgba(255,255,255,0.1)" />
              <XAxis
                dataKey="time"
                tick={{ fill: '#94a3b8', fontSize: 10 }}
                axisLine={{ stroke: 'rgba(255,255,255,0.2)' }}
                tickLine={false}
                interval="preserveStartEnd"
              />
              <YAxis
                tick={{ fill: '#94a3b8', fontSize: 10 }}
                axisLine={{ stroke: 'rgba(255,255,255,0.2)' }}
                tickLine={false}
                width={50}
              />
              <Tooltip content={<CustomTooltip />} />
              <Line
                type="monotone"
                dataKey="fuelFlow"
                stroke="#f59e0b"
                strokeWidth={2}
                dot={false}
                name="Fuel Flow"
                unit="kg/s"
              />
            </LineChart>
          </ResponsiveContainer>
        </div>
      </div>
    </div>
  );
}

export default TrajectoryChart;
