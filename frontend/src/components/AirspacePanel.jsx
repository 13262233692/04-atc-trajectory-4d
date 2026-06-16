import { useState } from 'react';

const DEFAULT_FORM = {
  name: '',
  reason: 'Temporary Restriction',
  level: 'PROHIBITED',
  minAltitude: 0,
  maxAltitude: 15000,
  effectiveFrom: new Date().toISOString().slice(0, 16),
  effectiveTo: new Date(Date.now() + 4 * 60 * 60 * 1000).toISOString().slice(0, 16),
  active: true,
};

export default function AirspacePanel({
  drawing,
  onStartDrawing,
  onCancelDrawing,
  pendingVertices,
  onSubmit,
  airspaces,
  onDeleteAirspace,
  onTriggerAvoidance,
  rerouteResult,
}) {
  const [form, setForm] = useState(DEFAULT_FORM);
  const [submitting, setSubmitting] = useState(false);

  const canSubmit = pendingVertices && pendingVertices.length >= 3;

  const update = (k, v) => setForm(prev => ({ ...prev, [k]: v }));

  const handleSubmit = async () => {
    if (!canSubmit || submitting) return;
    setSubmitting(true);
    try {
      await onSubmit({
        ...form,
        polygonVertices: pendingVertices.map(v => ({
          name: `V${pendingVertices.indexOf(v)}`,
          latitude: v.latitude,
          longitude: v.longitude,
          altitude: v.altitude || 10000,
        })),
        minAltitude: Number(form.minAltitude),
        maxAltitude: Number(form.maxAltitude),
        effectiveFrom: new Date(form.effectiveFrom).toISOString(),
        effectiveTo: new Date(form.effectiveTo).toISOString(),
      });
      setForm(DEFAULT_FORM);
    } finally {
      setSubmitting(false);
    }
  };

  return (
    <div className="h-full flex flex-col bg-slate-900/70 backdrop-blur-sm text-white">
      <div className="p-4 border-b border-white/10">
        <h2 className="text-lg font-bold flex items-center gap-2">
          <span className="text-red-400">⚠</span>
          Restricted Airspace
        </h2>
        <p className="text-xs text-gray-400 mt-1">
          Draw polygons to define no-fly zones and trigger reroutes
        </p>
      </div>

      <div className="flex-1 overflow-y-auto p-4 space-y-4">
        <div className="space-y-2">
          {!drawing ? (
            <button
              onClick={onStartDrawing}
              className="w-full bg-red-600 hover:bg-red-500 text-white py-2.5 rounded-lg font-semibold transition flex items-center justify-center gap-2"
            >
              <span>✎</span> Draw Polygon Restriction
            </button>
          ) : (
            <div className="space-y-2">
              <div className="bg-amber-500/15 border border-amber-400/40 text-amber-200 text-sm p-3 rounded-lg">
                <div className="font-semibold mb-1">Drawing in progress</div>
                <div className="text-xs space-y-0.5 text-amber-300/90">
                  <div>• Left-click to add vertices</div>
                  <div>• Double-click to finish</div>
                  <div>• Right-click to cancel</div>
                  <div className="mt-1 font-mono">
                    Vertices added: <span className="text-white">{pendingVertices?.length || 0}</span>
                  </div>
                </div>
              </div>
              <button
                onClick={onCancelDrawing}
                className="w-full bg-slate-700 hover:bg-slate-600 py-2 rounded-lg font-medium"
              >
                Cancel Drawing
              </button>
            </div>
          )}
        </div>

        {(drawing || canSubmit) && (
          <div className="space-y-3 bg-slate-800/60 rounded-lg p-3 border border-white/10">
            <div className="text-sm font-semibold text-gray-200">Zone parameters</div>

            <div>
              <label className="block text-xs text-gray-400 mb-1">Name</label>
              <input
                value={form.name}
                onChange={e => update('name', e.target.value)}
                placeholder="e.g. THUNDERSTORM-01"
                className="w-full bg-slate-900 border border-white/10 rounded px-3 py-1.5 text-sm"
              />
            </div>

            <div>
              <label className="block text-xs text-gray-400 mb-1">Reason</label>
              <input
                value={form.reason}
                onChange={e => update('reason', e.target.value)}
                className="w-full bg-slate-900 border border-white/10 rounded px-3 py-1.5 text-sm"
              />
            </div>

            <div>
              <label className="block text-xs text-gray-400 mb-1">Restriction level</label>
              <select
                value={form.level}
                onChange={e => update('level', e.target.value)}
                className="w-full bg-slate-900 border border-white/10 rounded px-3 py-1.5 text-sm"
              >
                <option value="PROHIBITED">🚫 Prohibited</option>
                <option value="WARNING">⚠ Warning</option>
                <option value="ADVISORY">ℹ Advisory</option>
              </select>
            </div>

            <div className="grid grid-cols-2 gap-2">
              <div>
                <label className="block text-xs text-gray-400 mb-1">Min Alt (m)</label>
                <input
                  type="number"
                  value={form.minAltitude}
                  onChange={e => update('minAltitude', e.target.value)}
                  className="w-full bg-slate-900 border border-white/10 rounded px-3 py-1.5 text-sm"
                />
              </div>
              <div>
                <label className="block text-xs text-gray-400 mb-1">Max Alt (m)</label>
                <input
                  type="number"
                  value={form.maxAltitude}
                  onChange={e => update('maxAltitude', e.target.value)}
                  className="w-full bg-slate-900 border border-white/10 rounded px-3 py-1.5 text-sm"
                />
              </div>
            </div>

            <div className="grid grid-cols-2 gap-2">
              <div>
                <label className="block text-xs text-gray-400 mb-1">From</label>
                <input
                  type="datetime-local"
                  value={form.effectiveFrom}
                  onChange={e => update('effectiveFrom', e.target.value)}
                  className="w-full bg-slate-900 border border-white/10 rounded px-3 py-1.5 text-sm"
                />
              </div>
              <div>
                <label className="block text-xs text-gray-400 mb-1">Until</label>
                <input
                  type="datetime-local"
                  value={form.effectiveTo}
                  onChange={e => update('effectiveTo', e.target.value)}
                  className="w-full bg-slate-900 border border-white/10 rounded px-3 py-1.5 text-sm"
                />
              </div>
            </div>

            <label className="flex items-center gap-2 text-sm">
              <input
                type="checkbox"
                checked={form.active}
                onChange={e => update('active', e.target.checked)}
              />
              <span>Active immediately</span>
            </label>

            <button
              onClick={handleSubmit}
              disabled={!canSubmit || submitting}
              className="w-full bg-emerald-600 hover:bg-emerald-500 disabled:bg-slate-700 disabled:text-gray-500 py-2.5 rounded-lg font-semibold transition"
            >
              {submitting ? 'Creating…' : `Create Zone (${pendingVertices?.length || 0} vertices)`}
            </button>
          </div>
        )}

        {rerouteResult && (
          <div className={`rounded-lg p-3 border text-sm space-y-1 ${
            rerouteResult.success
              ? 'bg-emerald-500/10 border-emerald-400/30'
              : 'bg-red-500/10 border-red-400/30'
          }`}>
            <div className="font-semibold">
              {rerouteResult.success ? '✅ Reroute successful' : '❌ Reroute failed'}
            </div>
            <div className="text-xs text-gray-300">Flight: {rerouteResult.flightId}</div>
            {rerouteResult.success && (
              <div className="text-xs font-mono text-gray-200 space-y-0.5">
                <div>Extra distance: {rerouteResult.extraDistanceMeters?.toFixed(0)} m</div>
                <div>Extra time: {rerouteResult.extraTimeSeconds?.toFixed(0)} s</div>
                <div>Waypoints: {rerouteResult.detourRoute?.length}</div>
              </div>
            )}
            {rerouteResult.message && (
              <div className="text-xs text-gray-400 mt-1">{rerouteResult.message}</div>
            )}
          </div>
        )}

        <div className="space-y-2">
          <div className="flex justify-between items-center">
            <h3 className="text-sm font-semibold text-gray-200">Active zones</h3>
            <span className="text-xs text-gray-500">{airspaces.length} total</span>
          </div>
          {airspaces.length === 0 ? (
            <div className="text-xs text-gray-500 italic p-3 border border-dashed border-white/10 rounded text-center">
              No restricted zones defined yet
            </div>
          ) : (
            <div className="space-y-2">
              {airspaces.map(a => (
                <div
                  key={a.id}
                  className={`p-3 rounded-lg border ${
                    a.active ? 'border-red-400/30 bg-red-500/5' : 'border-gray-600/30 bg-slate-800/30'
                  }`}
                >
                  <div className="flex items-start justify-between gap-2">
                    <div className="min-w-0">
                      <div className="font-semibold text-sm truncate">{a.name || a.id}</div>
                      <div className="text-xs text-gray-400 truncate">
                        {a.level} · {a.polygonVertices?.length} pts · {(a.minAltitude||0).toFixed(0)}–{(a.maxAltitude||0).toFixed(0)}m
                      </div>
                      {a.reason && (
                        <div className="text-xs text-gray-500 truncate">{a.reason}</div>
                      )}
                    </div>
                    <div className={`w-2.5 h-2.5 rounded-full ${a.active ? 'bg-red-500 animate-pulse' : 'bg-gray-500'}`} />
                  </div>
                  <div className="flex gap-2 mt-2">
                    <button
                      onClick={() => onTriggerAvoidance(a.id)}
                      className="flex-1 text-xs bg-cyan-600/70 hover:bg-cyan-500 py-1.5 rounded"
                    >
                      Trigger Reroute
                    </button>
                    <button
                      onClick={() => onDeleteAirspace(a.id)}
                      className="text-xs bg-red-600/60 hover:bg-red-500 px-3 py-1.5 rounded"
                    >
                      Delete
                    </button>
                  </div>
                </div>
              ))}
            </div>
          )}
        </div>
      </div>
    </div>
  );
}
