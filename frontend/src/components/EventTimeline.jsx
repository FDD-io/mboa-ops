import { formatHeure } from '../api';
import { ConfidenceBadge } from './Badges';

const EVENEMENTS_DEGRADES = new Set(['CONFIANCE_DEGRADEE', 'CANAL_DEGRADE']);

function resumePayload(payload) {
  if (payload === null || payload === undefined) return '';
  if (typeof payload === 'string') return payload;
  try {
    return JSON.stringify(payload);
  } catch {
    return String(payload);
  }
}

export function EventTimeline({ events }) {
  return (
    <section className="flex min-h-0 flex-1 flex-col rounded-xl border border-slate-200 bg-white p-4 shadow-sm">
      <h2 className="text-sm font-semibold text-slate-700">
        Flux d'événements
        <span className="ml-2 font-normal text-slate-400">
          {events?.length ?? 0} événements — temps réel
        </span>
      </h2>
      <ol className="scrollbar-thin mt-3 min-h-0 flex-1 space-y-1.5 overflow-y-auto pr-1">
        {(events || []).map((e) => {
          const degrade = EVENEMENTS_DEGRADES.has(e.type);
          return (
            <li
              key={e.id}
              className={`rounded-lg border px-3 py-2 text-sm ${
                degrade
                  ? 'border-amber-300 bg-amber-50'
                  : 'border-slate-100 bg-slate-50'
              }`}
            >
              <div className="flex flex-wrap items-center gap-2">
                <span
                  className={`font-mono text-xs font-semibold ${
                    degrade ? 'text-amber-800' : 'text-slate-700'
                  }`}
                >
                  {e.type}
                </span>
                <ConfidenceBadge value={e.confidence} />
                <span className="ml-auto text-xs tabular-nums text-slate-400">
                  {formatHeure(e.createdAt)}
                </span>
              </div>
              <p className="mt-1 truncate text-xs text-slate-500">
                {resumePayload(e.payload)}
              </p>
              {e.reasoning && (
                <details className="mt-1">
                  <summary className="cursor-pointer text-xs font-medium text-emerald-700 hover:text-emerald-800">
                    Raisonnement
                  </summary>
                  <p className="mt-1 whitespace-pre-line rounded-md bg-white p-2 text-xs text-slate-600">
                    {e.reasoning}
                  </p>
                </details>
              )}
            </li>
          );
        })}
      </ol>
    </section>
  );
}
