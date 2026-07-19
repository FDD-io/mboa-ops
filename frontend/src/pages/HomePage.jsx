import { api } from '../api';
import { usePolling } from '../hooks/usePolling';
import { ChatSimulator } from '../components/ChatSimulator';
import { CircuitPanel } from '../components/CircuitPanel';
import { CommandesPanel } from '../components/CommandesPanel';
import { ErrorBanner } from '../components/ErrorBanner';
import { EventTimeline } from '../components/EventTimeline';

export function HomePage() {
  const clients = usePolling(api.clients, 10000);
  const commandes = usePolling(api.commandes, 2000);
  const events = usePolling(() => api.events(200), 2000);
  const circuits = usePolling(api.circuits, 2000);

  const erreur =
    commandes.error || events.error || circuits.error || clients.error;

  function rafraichir() {
    commandes.refresh();
    events.refresh();
    circuits.refresh();
  }

  return (
    <div className="flex h-full flex-col">
      <ErrorBanner message={erreur} />
      <div className="grid min-h-0 flex-1 grid-cols-1 gap-4 p-4 lg:grid-cols-3">
        <div className="min-h-0 lg:col-span-1">
          <ChatSimulator
            clients={clients.data}
            commandes={commandes.data}
            events={events.data}
            onAction={rafraichir}
          />
        </div>
        <div className="flex min-h-0 flex-col gap-4 lg:col-span-2">
          <CircuitPanel circuits={circuits.data} onAction={rafraichir} />
          <EventTimeline events={events.data} />
          <CommandesPanel commandes={commandes.data} />
        </div>
      </div>
    </div>
  );
}
