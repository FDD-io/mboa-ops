import { useEffect, useMemo, useRef, useState } from 'react';
import { api, formatHeure } from '../api';
import { MomoModal } from './MomoModal';

/**
 * Le fil de discussion est reconstruit à chaque polling depuis l'event
 * store : messages entrants (MESSAGE_RECU) côté client, réponses de
 * l'agent (clarifications, devis, confirmations de paiement) côté agent.
 */
function construireFil(commandes, events, telephone) {
  if (!commandes || !events) return [];
  const idsClient = new Set(
    commandes.filter((c) => c.clientTelephone === telephone).map((c) => c.id),
  );
  const bulles = [];
  for (const e of events) {
    // Les événements de commande sont rattachés à l'id de la commande ;
    // ceux d'un simple message (question, classification) portent le
    // téléphone du client dans leur payload.
    const concerneClient =
      idsClient.has(e.aggregateId) || e.payload?.clientPhone === telephone;
    if (!concerneClient) continue;
    if (e.type === 'MESSAGE_RECU') {
      const p = e.payload || {};
      const morceaux = [];
      if (p.content) morceaux.push(p.content);
      if (p.texte) morceaux.push(p.texte);
      if (p.hasAudio) morceaux.push('🎤 Message vocal');
      if (p.hasImage) morceaux.push('📷 Photo de liste');
      bulles.push({
        id: e.id,
        de: 'client',
        texte: morceaux.join('\n') || '(message vide)',
        heure: e.createdAt,
      });
    } else if (e.type === 'MESSAGE_CLARIFICATION_GENERE') {
      // payload : chaîne (historique) ou {message, durationMs} (instrumenté)
      const texte =
        typeof e.payload === 'string' ? e.payload : e.payload?.message || '';
      bulles.push({ id: e.id, de: 'agent', texte, heure: e.createdAt });
    } else if (e.type === 'REPONSE_QUESTION_ENVOYEE') {
      bulles.push({
        id: e.id,
        de: 'agent',
        texte: e.payload?.reponse || '',
        heure: e.createdAt,
      });
    } else if (
      e.type === 'MESSAGE_ATTENTE_ENVOYE' ||
      e.type === 'REPONSE_PATRON_REFORMULEE' ||
      e.type === 'MESSAGE_REFUS_ENVOYE'
    ) {
      bulles.push({
        id: e.id,
        de: 'agent',
        texte: e.payload?.message || '',
        heure: e.createdAt,
      });
    } else if (e.type === 'DEVIS_GENERE') {
      bulles.push({ id: e.id, de: 'agent', texte: e.payload?.devis || '', heure: e.createdAt });
    } else if (e.type === 'PAIEMENT_RECONCILIE') {
      const p = e.payload || {};
      bulles.push({
        id: e.id,
        de: 'agent',
        texte: `✅ Paiement reçu : ${p.montant} FCFA (réf. ${p.reference})${p.acompte ? ' — acompte de 50%' : ''}. Merci !`,
        heure: e.createdAt,
      });
    }
  }
  return bulles.sort((a, b) => new Date(a.heure) - new Date(b.heure));
}

export function ChatSimulator({ clients, commandes, events, onAction }) {
  const [telephone, setTelephone] = useState('');
  const [texte, setTexte] = useState('');
  const [audio, setAudio] = useState(null);
  const [image, setImage] = useState(null);
  const [envoiEnCours, setEnvoiEnCours] = useState(false);
  const [erreur, setErreur] = useState(null);
  const [momoOuvert, setMomoOuvert] = useState(false);
  const [infoSysteme, setInfoSysteme] = useState(null);
  const finRef = useRef(null);
  const audioInputRef = useRef(null);
  const imageInputRef = useRef(null);

  useEffect(() => {
    if (!telephone && clients?.length) setTelephone(clients[0].telephone);
  }, [clients, telephone]);

  const clientActif = clients?.find((c) => c.telephone === telephone);
  const bulles = useMemo(
    () => construireFil(commandes, events, telephone),
    [commandes, events, telephone],
  );

  useEffect(() => {
    finRef.current?.scrollIntoView({ behavior: 'smooth', block: 'end' });
  }, [bulles.length]);

  async function envoyer(e) {
    e.preventDefault();
    if (!telephone || (!texte.trim() && !audio && !image)) return;
    setErreur(null);
    setEnvoiEnCours(true);
    try {
      if (audio || image) {
        await api.envoyerMultipart(telephone, {
          content: texte.trim() || undefined,
          audio,
          image,
        });
      } else {
        await api.envoyerTexte(telephone, texte.trim());
      }
      setTexte('');
      setAudio(null);
      setImage(null);
      if (audioInputRef.current) audioInputRef.current.value = '';
      if (imageInputRef.current) imageInputRef.current.value = '';
      onAction?.();
    } catch (err) {
      setErreur(err.message);
    } finally {
      setEnvoiEnCours(false);
    }
  }

  function surResultatMomo(result) {
    setInfoSysteme(
      result.reconcilie
        ? `Paiement de ${result.montant} FCFA réconcilié (${result.reference})`
        : `Paiement non réconcilié : ${result.motif}`,
    );
    onAction?.();
    setTimeout(() => setInfoSysteme(null), 6000);
  }

  return (
    <section className="flex h-full flex-col overflow-hidden rounded-xl border border-slate-200 bg-white shadow-sm">
      <header className="flex items-center gap-3 border-b border-emerald-700 bg-emerald-600 px-4 py-3 text-white">
        <div className="flex h-9 w-9 items-center justify-center rounded-full bg-emerald-500 text-sm font-bold">
          {clientActif?.nom?.charAt(clientActif.nom.indexOf(' ') + 1) || '?'}
        </div>
        <div className="min-w-0 flex-1">
          <label htmlFor="chat-client" className="sr-only">
            Client simulé
          </label>
          <select
            id="chat-client"
            value={telephone}
            onChange={(e) => setTelephone(e.target.value)}
            className="w-full rounded-md border-0 bg-emerald-600 text-sm font-semibold text-white focus:ring-2 focus:ring-white"
          >
            {(clients || []).map((c) => (
              <option key={c.id} value={c.telephone}>
                {c.nom} — {c.telephone}
              </option>
            ))}
          </select>
          <p className="text-xs text-emerald-100">
            Simulateur WhatsApp — vous jouez le client
          </p>
        </div>
        <button
          type="button"
          onClick={() => setMomoOuvert(true)}
          className="rounded-lg bg-emerald-700 px-3 py-1.5 text-xs font-semibold hover:bg-emerald-800"
        >
          Simuler paiement MoMo
        </button>
      </header>

      <div className="chat-canvas scrollbar-thin flex-1 space-y-2 overflow-y-auto p-4">
        {bulles.length === 0 && (
          <p className="mt-8 text-center text-sm text-slate-500">
            Aucun message pour ce client. Envoyez une commande pour démarrer.
          </p>
        )}
        {bulles.map((b) => (
          <div
            key={b.id}
            className={`flex ${b.de === 'client' ? 'justify-end' : 'justify-start'}`}
          >
            <div
              className={`max-w-[85%] whitespace-pre-line rounded-2xl px-3 py-2 text-sm shadow-sm ${
                b.de === 'client'
                  ? 'rounded-br-sm bg-emerald-100 text-slate-900'
                  : 'rounded-bl-sm bg-white text-slate-900'
              }`}
            >
              {b.texte}
              <div className="mt-1 text-right text-[10px] text-slate-400">
                {formatHeure(b.heure)}
              </div>
            </div>
          </div>
        ))}
        {infoSysteme && (
          <p className="mx-auto w-fit rounded-full bg-slate-200/80 px-3 py-1 text-center text-xs text-slate-600">
            {infoSysteme}
          </p>
        )}
        <div ref={finRef} />
      </div>

      <form onSubmit={envoyer} className="border-t border-slate-200 bg-slate-50 p-3">
        {erreur && <p className="mb-2 text-xs text-red-600">Envoi impossible : {erreur}</p>}
        <div className="flex items-end gap-2">
          <div className="flex-1">
            <label htmlFor="chat-texte" className="sr-only">
              Message
            </label>
            <textarea
              id="chat-texte"
              rows={2}
              value={texte}
              onChange={(e) => setTexte(e.target.value)}
              placeholder="Écrire un message…"
              className="w-full resize-none rounded-lg border border-slate-300 px-3 py-2 text-sm focus:border-emerald-500 focus:outline-none focus:ring-1 focus:ring-emerald-500"
            />
            <div className="mt-1 flex flex-wrap items-center gap-3 text-xs text-slate-600">
              <label className="cursor-pointer hover:text-emerald-700">
                🎤 Vocal
                <input
                  ref={audioInputRef}
                  type="file"
                  accept="audio/*"
                  className="sr-only"
                  onChange={(e) => setAudio(e.target.files?.[0] || null)}
                />
              </label>
              {audio && <span className="text-emerald-700">{audio.name}</span>}
              <label className="cursor-pointer hover:text-emerald-700">
                📷 Photo
                <input
                  ref={imageInputRef}
                  type="file"
                  accept="image/*"
                  className="sr-only"
                  onChange={(e) => setImage(e.target.files?.[0] || null)}
                />
              </label>
              {image && <span className="text-emerald-700">{image.name}</span>}
            </div>
          </div>
          <button
            type="submit"
            disabled={envoiEnCours}
            className="rounded-full bg-emerald-600 px-4 py-2.5 text-sm font-semibold text-white hover:bg-emerald-700 disabled:opacity-50"
          >
            {envoiEnCours ? '…' : 'Envoyer'}
          </button>
        </div>
      </form>

      {momoOuvert && (
        <MomoModal
          clientNom={clientActif?.nom?.replace(/^(Mme|M\.)\s*/i, '').toUpperCase()}
          onClose={() => setMomoOuvert(false)}
          onResult={surResultatMomo}
        />
      )}
    </section>
  );
}
