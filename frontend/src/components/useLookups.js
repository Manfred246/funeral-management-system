import { useEffect, useState } from 'react';
import { api } from '../api/client';

export default function useLookups() {
  const [data, setData] = useState({ clients: [], types: [], tags: [] });
  const [loading, setLoading] = useState(true);
  const [error, setError] = useState(null);
  const [revision, setRevision] = useState(0);
  useEffect(() => {
    const controller = new AbortController();
    setLoading(true);
    setError(null);
    Promise.all(
      ['/api/clients', '/api/ceremony-types', '/api/tags'].map((path) =>
        api(path, { signal: controller.signal }),
      ),
    )
      .then(([clients, types, tags]) => setData({ clients, types, tags }))
      .catch((error) => {
        if (error.name !== 'AbortError') setError(error);
      })
      .finally(() => {
        if (!controller.signal.aborted) setLoading(false);
      });
    return () => controller.abort();
  }, [revision]);
  return { ...data, loading, error, reload: () => setRevision((value) => value + 1) };
}
