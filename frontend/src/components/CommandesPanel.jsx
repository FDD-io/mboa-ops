import { formatFcfa, formatHeure } from '../api';
import { StatutBadge } from './Badges';

export function CommandesPanel({ commandes }) {
  const recentes = [...(commandes || [])]
    .sort((a, b) => new Date(b.dateCreation) - new Date(a.dateCreation))
    .slice(0, 12);

  return (
    <section className="rounded-xl border border-slate-200 bg-white p-4 shadow-sm">
      <h2 className="text-sm font-semibold text-slate-700">Commandes</h2>
      {recentes.length === 0 ? (
        <p className="mt-3 text-sm text-slate-500">
          Aucune commande. Envoyez un message depuis le simulateur.
        </p>
      ) : (
        <ul className="mt-3 divide-y divide-slate-100">
          {recentes.map((c) => (
            <li key={c.id} className="flex items-center gap-3 py-2 text-sm">
              <div className="min-w-0 flex-1">
                <span className="font-medium">{c.clientNom}</span>
                <span className="ml-2 text-xs text-slate-400">
                  {formatHeure(c.dateCreation)}
                </span>
                <p className="truncate text-xs text-slate-500">
                  {(c.lignes || [])
                    .map((l) => `${l.produitNom} ×${l.quantite}`)
                    .join(', ') || '—'}
                </p>
              </div>
              <span className="tabular-nums text-sm font-semibold">
                {formatFcfa(c.montantTotal)}
              </span>
              <StatutBadge statut={c.statut} />
            </li>
          ))}
        </ul>
      )}
    </section>
  );
}
