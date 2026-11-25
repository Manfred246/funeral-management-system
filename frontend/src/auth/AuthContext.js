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
