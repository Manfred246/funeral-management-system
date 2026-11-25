import { createContext, useContext } from 'react';

export const AuthContext = createContext(null);
export function useAuth() {
  return useContext(AuthContext);
}
export function canManage(user) {
  return user?.roles?.some((role) => role === 'MANAGER' || role === 'ADMIN') || false;
}
export function isAdmin(user) {
  return user?.roles?.includes('ADMIN') || false;
}

export function loginDestination(user, from) {
  if (user.passwordChangeRequired) return '/change-password';
  if (typeof from === 'string' && from.startsWith('/') && !from.startsWith('//') && from !== '/login') {
    return from;
  }
  return '/requests';
}
