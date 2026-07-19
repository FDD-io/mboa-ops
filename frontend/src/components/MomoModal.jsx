import { useState } from 'react';
import { api } from '../api';

function genererReference() {
  const now = new Date();
  const yymmdd = now.toISOString().slice(2, 10).replaceAll('-', '');
  const serie = Math.floor(1000 + Math.random() * 9000);
  const suffixe = Math.random().toString(36).slice(2, 8).toUpperCase();
  return `MP${yymmdd}.${serie}.${suffixe}`;
}

export function MomoModal({ clientNom, onClose, onResult }) {
  const [montant, setMontant] = useState('');
  const [expediteur, setExpediteur] = useState(clientNom || '');
  const [envoiEnCours, setEnvoiEnCours] = useState(false);
  const [erreur, setErreur] = useState(null);

  async function envoyer(e) {
    e.preventDefault();
    setErreur(null);
    setEnvoiEnCours(true);
    const sms = `Vous avez recu ${montant} FCFA de ${expediteur.toUpperCase()}. ID: ${genererReference()}. Nouveau solde: ${montant} FCFA. Frais: 0 FCFA.`;
    try {
      const result = await api.webhookMomo(sms);
      onResult(result, sms);
      onClose();
    } catch (err) {
      setErreur(err.message);
    } finally {
      setEnvoiEnCours(false);
    }
  }

  return (
    <div
      className="fixed inset-0 z-50 flex items-center justify-center bg-slate-900/50 p-4"
      onClick={onClose}
    >
      <form
        onClick={(e) => e.stopPropagation()}
        onSubmit={envoyer}
        className="w-full max-w-sm rounded-xl bg-white p-5 shadow-xl"
      >
        <h3 className="text-base font-semibold">Simuler un paiement MoMo</h3>
        <p className="mt-1 text-xs text-slate-500">
          Envoie un SMS brut au format MTN à l'API de réconciliation.
        </p>

        <label className="mt-4 block text-sm font-medium" htmlFor="momo-montant">
          Montant (FCFA)
        </label>
        <input
          id="momo-montant"
          type="number"
          min="1"
          required
          value={montant}
          onChange={(e) => setMontant(e.target.value)}
          className="mt-1 w-full rounded-lg border border-slate-300 px-3 py-2 text-sm focus:border-emerald-500 focus:outline-none focus:ring-1 focus:ring-emerald-500"
          placeholder="45000"
        />

        <label className="mt-3 block text-sm font-medium" htmlFor="momo-exp">
          Nom de l'expéditeur
        </label>
        <input
          id="momo-exp"
          type="text"
          required
          value={expediteur}
          onChange={(e) => setExpediteur(e.target.value)}
          className="mt-1 w-full rounded-lg border border-slate-300 px-3 py-2 text-sm focus:border-emerald-500 focus:outline-none focus:ring-1 focus:ring-emerald-500"
          placeholder="NGO MARIE"
        />

        {erreur && <p className="mt-2 text-sm text-red-600">{erreur}</p>}

        <div className="mt-5 flex justify-end gap-2">
          <button
            type="button"
            onClick={onClose}
            className="rounded-lg px-3 py-2 text-sm font-medium text-slate-600 hover:bg-slate-100"
          >
            Annuler
          </button>
          <button
            type="submit"
            disabled={envoiEnCours}
            className="rounded-lg bg-emerald-600 px-4 py-2 text-sm font-semibold text-white hover:bg-emerald-700 disabled:opacity-50"
          >
            {envoiEnCours ? 'Envoi…' : 'Envoyer le SMS'}
          </button>
        </div>
      </form>
    </div>
  );
}
