import { useState } from 'react';
import { api, formatFcfa } from '../api';
import { usePolling } from '../hooks/usePolling';
import { ConfidenceBadge } from '../components/Badges';
import { ErrorBanner } from '../components/ErrorBanner';

function DecisionCardItem({ card, onDone }) {
  const [commentaire, setCommentaire] = useState('');
  const [modifierOuvert, setModifierOuvert] = useState(false);
  const [enCours, setEnCours] = useState(null);
  const [erreur, setErreur] = useState(null);

  async function repondre(action) {
    if (action === 'MODIFY' && !commentaire.trim()) {
      setModifierOuvert(true);
      setErreur('Ajoutez un commentaire pour préciser la modification.');
      return;
    }
    setEnCours(action);
    setErreur(null);
    try {
      await api.repondreDecision(card.id, action, commentaire.trim() || null);
      onDone?.();
    } catch (e) {
      setErreur(e.message);
    } finally {
      setEnCours(null);
    }
  }

  return (
    <article className="rounded-xl border border-slate-200 bg-white p-4 shadow-sm">
      <header className="flex items-start justify-between gap-2">
        <div>
          <h3 className="font-semibold">{card.clientNom}</h3>
          <p className="text-sm tabular-nums text-slate-500">
            {formatFcfa(card.montantTotal)}
          </p>
        </div>
        <ConfidenceBadge value={card.confidence} />
      </header>

      <p className="mt-3 whitespace-pre-line border-l-2 border-slate-200 pl-3 text-sm text-slate-700">
        {card.resume}
      </p>

      <div className="mt-3 rounded-lg bg-emerald-50 p-3">
        <p className="text-xs font-semibold uppercase tracking-wide text-emerald-700">
          Recommandation de l'agent
        </p>
        <p className="mt-1 text-sm text-emerald-900">{card.recommandation}</p>
      </div>

      {(modifierOuvert || commentaire) && (
        <div className="mt-3">
          <label htmlFor={`comm-${card.id}`} className="text-xs font-medium text-slate-600">
            Commentaire
          </label>
          <textarea
            id={`comm-${card.id}`}
            rows={2}
            value={commentaire}
            onChange={(e) => setCommentaire(e.target.value)}
            className="mt-1 w-full rounded-lg border border-slate-300 px-3 py-2 text-sm focus:border-emerald-500 focus:outline-none focus:ring-1 focus:ring-emerald-500"
            placeholder="Ex. : OK avec acompte 50% confirmé au téléphone"
          />
        </div>
      )}

      {erreur && <p className="mt-2 text-sm text-red-600">{erreur}</p>}

      <div className="mt-4 grid grid-cols-3 gap-2">
        <button
          type="button"
          onClick={() => repondre('APPROVE')}
          disabled={enCours !== null}
          className="rounded-xl bg-emerald-600 py-3 text-sm font-bold text-white hover:bg-emerald-700 disabled:opacity-50"
        >
          {enCours === 'APPROVE' ? '…' : '✅ Approuver'}
        </button>
        <button
          type="button"
          onClick={() => repondre('REJECT')}
          disabled={enCours !== null}
          className="rounded-xl border-2 border-red-200 bg-red-50 py-3 text-sm font-bold text-red-700 hover:bg-red-100 disabled:opacity-50"
        >
          {enCours === 'REJECT' ? '…' : '❌ Refuser'}
        </button>
        <button
          type="button"
          onClick={() =>
            modifierOuvert ? repondre('MODIFY') : setModifierOuvert(true)
          }
          disabled={enCours !== null}
          className="rounded-xl border-2 border-slate-200 bg-slate-50 py-3 text-sm font-bold text-slate-700 hover:bg-slate-100 disabled:opacity-50"
        >
          {enCours === 'MODIFY' ? '…' : '✏️ Modifier'}
        </button>
      </div>
    </article>
  );
}

function PreferenceItem({ pref, onRevoked }) {
  const [confirmation, setConfirmation] = useState(false);
  const [enCours, setEnCours] = useState(false);
  const [erreur, setErreur] = useState(null);
  const revoquee = pref.statut === 'REVOQUEE';

  async function revoquer() {
    setEnCours(true);
    setErreur(null);
    try {
      await api.revoquerPreference(pref.id);
      setConfirmation(false);
      onRevoked?.();
    } catch (e) {
      setErreur(e.message);
    } finally {
      setEnCours(false);
    }
  }

  return (
    <li
      className={`flex flex-wrap items-center gap-2 rounded-lg border px-3 py-2.5 text-sm ${
        revoquee
          ? 'border-slate-100 bg-slate-50 text-slate-400'
          : 'border-slate-200 bg-white'
      }`}
    >
      <div className="min-w-0 flex-1">
        <span className="font-medium">{pref.clientNom}</span>
        <span className="ml-2 tabular-nums">
          plafond {formatFcfa(pref.plafond)}
        </span>
        <span className="ml-2 text-xs">
          {pref.compteur} approbation{pref.compteur > 1 ? 's' : ''}
        </span>
      </div>
      <span
        className={`rounded-full px-2 py-0.5 text-xs font-semibold ${
          revoquee ? 'bg-slate-200 text-slate-500' : 'bg-emerald-100 text-emerald-800'
        }`}
      >
        {pref.statut}
      </span>
      {!revoquee && (
        <button
          type="button"
          onClick={() => setConfirmation(true)}
          className="rounded-md px-2 py-1 text-xs font-semibold text-red-700 hover:bg-red-50"
        >
          Révoquer
        </button>
      )}
      {erreur && <p className="w-full text-xs text-red-600">{erreur}</p>}

      {confirmation && (
        <div
          className="fixed inset-0 z-50 flex items-center justify-center bg-slate-900/50 p-4"
          onClick={() => setConfirmation(false)}
        >
          <div
            onClick={(e) => e.stopPropagation()}
            className="w-full max-w-sm rounded-xl bg-white p-5 text-slate-900 shadow-xl"
          >
            <h3 className="text-base font-semibold">Révoquer cette préférence ?</h3>
            <p className="mt-2 text-sm text-slate-600">
              Les commandes de {pref.clientNom} repasseront par la validation
              manuelle. L'historique reste conservé, et 3 nouvelles approbations
              pourront ré-apprendre la règle.
            </p>
            <div className="mt-4 flex justify-end gap-2">
              <button
                type="button"
                onClick={() => setConfirmation(false)}
                className="rounded-lg px-3 py-2 text-sm font-medium text-slate-600 hover:bg-slate-100"
              >
                Annuler
              </button>
              <button
                type="button"
                onClick={revoquer}
                disabled={enCours}
                className="rounded-lg bg-red-600 px-4 py-2 text-sm font-semibold text-white hover:bg-red-700 disabled:opacity-50"
              >
                {enCours ? '…' : 'Révoquer'}
              </button>
            </div>
          </div>
        </div>
      )}
    </li>
  );
}

export function PatronPage() {
  const decisions = usePolling(api.decisionsPending, 2000);
  const preferences = usePolling(api.preferences, 2000);
  const erreur = decisions.error || preferences.error;

  return (
    <div className="mx-auto w-full max-w-2xl">
      <ErrorBanner message={erreur} />
      <div className="space-y-6 p-4">
        <section>
          <h2 className="text-base font-semibold">Décisions en attente</h2>
          {(decisions.data || []).length === 0 ? (
            <p className="mt-2 rounded-xl border border-dashed border-slate-300 p-6 text-center text-sm text-slate-500">
              Aucune décision en attente. L'agent gère tout seul pour l'instant.
            </p>
          ) : (
            <div className="mt-3 space-y-4">
              {decisions.data.map((card) => (
                <DecisionCardItem
                  key={card.id}
                  card={card}
                  onDone={() => decisions.refresh()}
                />
              ))}
            </div>
          )}
        </section>

        <section>
          <h2 className="text-base font-semibold">Préférences apprises</h2>
          {(preferences.data || []).length === 0 ? (
            <p className="mt-2 rounded-xl border border-dashed border-slate-300 p-6 text-center text-sm text-slate-500">
              Aucune préférence apprise. Approuvez 3 commandes similaires d'un
              même client pour en créer une.
            </p>
          ) : (
            <ul className="mt-3 space-y-2">
              {preferences.data.map((p) => (
                <PreferenceItem
                  key={p.id}
                  pref={p}
                  onRevoked={() => preferences.refresh()}
                />
              ))}
            </ul>
          )}
        </section>
      </div>
    </div>
  );
}
