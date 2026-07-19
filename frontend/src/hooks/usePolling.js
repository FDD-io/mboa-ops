import { useCallback, useEffect, useRef, useState } from 'react';

/**
 * Interroge `fetcher` immédiatement puis toutes les `intervalMs`.
 * Tout l'état vient de l'API : aucun cache local, aucune persistance.
 */
export function usePolling(fetcher, intervalMs = 2000) {
  const [data, setData] = useState(null);
  const [error, setError] = useState(null);
  const fetcherRef = useRef(fetcher);
  fetcherRef.current = fetcher;

  const tick = useCallback(async () => {
    try {
      const result = await fetcherRef.current();
      setData(result);
      setError(null);
    } catch (e) {
      setError(e.message || 'API injoignable');
    }
  }, []);

  useEffect(() => {
    tick();
    const id = setInterval(tick, intervalMs);
    return () => clearInterval(id);
  }, [tick, intervalMs]);

  return { data, error, refresh: tick };
}
