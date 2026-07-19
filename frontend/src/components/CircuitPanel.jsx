import { useState } from 'react';
import { api } from '../api';

const ETAT_STYLES = {
  CLOSED: { dot: 'bg-emerald-500', label: 'text-emerald-700' },
  OPEN: { dot: 'bg-red-500', label: 'text-red-700' },
  FORCED_OPEN: { dot: 'bg-red-500', label: 'text-red-700' },
  HALF_OPEN: { dot: 'bg-amber-500', label: 'text-amber-700' },
};

export function CircuitPanel({ circuits, onAction }) {
  const [enCours, setEnCours] = useState(null);
  const [erreur, setErreur] = useState(null);

  async function basculer(nom) {
    setEnCours(nom);
    setErreur(null);
    try {
      await api.toggleChaos(nom);
      onAction?.();
    } catch (e) {
      setErreur(e.message);
    } finally {
      setEnCours(null);
    }
  }

  return (
    <section className="rounded-xl border border-slate-200 bg-white p-4 shadow-sm">
      <h2 className="text-sm font-semibold text-slate-700">Circuit breakers</h2>
      {erreur && <p className="mt-1 text-xs text-red-600">{erreur}</p>}
      <div className="mt-3 grid grid-cols-2 gap-2 xl:grid-cols-4">
        {(circuits || []).map((c) => {
          const style = ETAT_STYLES[c.etat] || ETAT_STYLES.HALF_OPEN;
          const force = c.etat === 'FORCED_OPEN';
          return (
            <div
              key={c.nom}
              className="flex flex-col gap-2 rounded-lg border border-slate-200 bg-slate-50 p-3"
            >
              <div className="flex items-center gap-2">
                <span className={`h-2.5 w-2.5 rounded-full ${style.dot}`} aria-hidden />
                <span className="text-sm font-semibold">{c.nom}</span>
              </div>
              <span className={`text-xs font-medium ${style.label}`}>{c.etat}</span>
              <button
                type="button"
                onClick={() => basculer(c.nom)}
                disabled={enCours === c.nom}
                className={`rounded-md px-2 py-1 text-xs font-semibold disabled:opacity-50 ${
                  force
                    ? 'bg-emerald-600 text-white hover:bg-emerald-700'
                    : 'bg-red-50 text-red-700 hover:bg-red-100'
                }`}
              >
                {enCours === c.nom ? '…' : force ? 'Rétablir' : '💥 Panne'}
              </button>
            </div>
          );
        })}
      </div>
    </section>
  );
}
