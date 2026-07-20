const STATUT_STYLES = {
  RECUE: 'bg-slate-100 text-slate-700',
  EXTRAITE: 'bg-sky-100 text-sky-800',
  EN_CLARIFICATION: 'bg-amber-100 text-amber-800',
  VALIDEE_STOCK: 'bg-sky-100 text-sky-800',
  EN_ATTENTE_PATRON: 'bg-violet-100 text-violet-800',
  APPROUVEE: 'bg-emerald-100 text-emerald-800',
  DEVIS_ENVOYE: 'bg-teal-100 text-teal-800',
  CREDIT_ACCORDE: 'bg-indigo-100 text-indigo-800',
  PAYEE: 'bg-emerald-600 text-white',
  LIVREE: 'bg-emerald-800 text-white',
  REJETEE: 'bg-red-100 text-red-800',
};

export function StatutBadge({ statut }) {
  const style = STATUT_STYLES[statut] || 'bg-slate-100 text-slate-700';
  return (
    <span
      className={`inline-block rounded-full px-2 py-0.5 text-xs font-medium ${style}`}
    >
      {statut?.replaceAll('_', ' ')}
    </span>
  );
}

export function ConfidenceBadge({ value }) {
  if (value === null || value === undefined) return null;
  const pct = Math.round(value * 100);
  const style =
    value > 0.9
      ? 'bg-emerald-100 text-emerald-800'
      : value >= 0.6
        ? 'bg-amber-100 text-amber-800'
        : 'bg-red-100 text-red-800';
  return (
    <span
      className={`inline-block rounded-full px-2 py-0.5 text-xs font-semibold tabular-nums ${style}`}
      title="Score de confiance"
    >
      {pct}%
    </span>
  );
}
