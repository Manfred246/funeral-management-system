import { useEffect, useState } from 'react';
import {
  api,
  hasRefreshSession,
  loginSession,
  logoutSession,
  refreshSession,
  setSessionListener,
} from '../api/client';
import { AuthContext } from './AuthContext';

export default function AuthProvider({ children }) {
  const [user, setUser] = useState(null);
  const [loading, setLoading] = useState(true);
  const [sessionError, setSessionError] = useState(null);

  useEffect(() => {
    let active = true;
    setSessionListener((value, error = null) => {
      if (active) {
        setUser((previous) => (value ? { ...previous, ...value } : null));
        setSessionError(error);
      }
    });
    async function restore() {
      try {
        if (hasRefreshSession()) {
          await refreshSession();
          const current = await api('/api/auth/me');
          if (active) setUser(current);
        }
      } catch (error) {
        if (active) setSessionError(error);
      } finally {
        if (active) setLoading(false);
      }
    }
    restore();
    return () => {
      active = false;
      setSessionListener(() => {});
    };
  }, []);

  async function login(login, password) {
    setSessionError(null);
    return loginSession(login, password);
  }

  async function reloadUser() {
    const current = await api('/api/auth/me');
    setUser(current);
  }

  return (
    <AuthContext.Provider value={{ user, loading, sessionError, login, reloadUser, logout: logoutSession }}>
      {children}
    </AuthContext.Provider>
  );
}
