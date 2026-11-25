import { Navigate, Outlet, useLocation } from 'react-router';
import { useAuth } from './AuthContext';

export default function RequireAuth({ roles }) {
  const { user, loading } = useAuth();
  const location = useLocation();
  if (loading)
    return (
      <div className="startup" role="status">
        Восстанавливаем сессию…
      </div>
    );
  if (!user) return <Navigate to="/login" state={{ from: location.pathname + location.search }} replace />;
  if (user.passwordChangeRequired && location.pathname !== '/change-password') {
    return <Navigate to="/change-password" replace />;
  }
  if (roles && !roles.some((role) => user.roles?.includes(role))) {
    return (
      <section className="card">
        <h1>Недостаточно прав</h1>
        <p>Эта страница недоступна вашей роли.</p>
      </section>
    );
  }
  return <Outlet />;
}
