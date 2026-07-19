const BASE_URL = import.meta.env.VITE_API_URL || 'http://localhost:8080';

async function request(path, options = {}) {
  const response = await fetch(`${BASE_URL}${path}`, options);
  if (!response.ok) {
    let detail = '';
    try {
      const body = await response.json();
      detail = body.message || body.error || '';
    } catch {
      /* corps non JSON */
    }
    throw new Error(`${response.status} ${detail}`.trim());
  }
  return response.status === 204 ? null : response.json();
}

export const api = {
  clients: () => request('/api/clients'),
  commandes: () => request('/api/commandes'),
  events: (limit = 200) => request(`/api/events?limit=${limit}`),
  eventsCommande: (id) => request(`/api/events/${id}`),
  circuits: () => request('/api/health/circuits'),
  decisionsPending: () => request('/api/decisions/pending'),
  preferences: () => request('/api/preferences'),

  envoyerTexte: (clientPhone, content) =>
    request('/api/messages', {
      method: 'POST',
      headers: { 'Content-Type': 'application/json' },
      body: JSON.stringify({ clientPhone, type: 'TEXT', content }),
    }),

  envoyerMultipart: (clientPhone, { content, audio, image }) => {
    const form = new FormData();
    form.append('clientPhone', clientPhone);
    if (content) form.append('content', content);
    if (audio) form.append('audio', audio);
    if (image) form.append('image', image);
    return request('/api/messages', { method: 'POST', body: form });
  },

  repondreDecision: (cardId, action, commentaire) =>
    request(`/api/decisions/${cardId}/respond`, {
      method: 'POST',
      headers: { 'Content-Type': 'application/json' },
      body: JSON.stringify({ action, commentaire }),
    }),

  revoquerPreference: (id) =>
    request(`/api/preferences/${id}`, { method: 'DELETE' }),

  toggleChaos: (service) =>
    request(`/api/chaos/${service}/toggle`, { method: 'POST' }),

  webhookMomo: (sms) =>
    request('/api/momo/webhook', {
      method: 'POST',
      headers: { 'Content-Type': 'application/json' },
      body: JSON.stringify({ sms }),
    }),
};

export function formatFcfa(montant) {
  if (montant === null || montant === undefined) return '—';
  return `${new Intl.NumberFormat('fr-FR').format(montant)} FCFA`;
}

export function formatHeure(iso) {
  return new Date(iso).toLocaleTimeString('fr-FR', {
    hour: '2-digit',
    minute: '2-digit',
    second: '2-digit',
  });
}
