import { Navigate, Outlet, useLocation } from 'react-router-dom';
import { useAuthStore } from '@/stores/authStore';

export function RequireAdmin() {
  const location = useLocation();
  const isAuthenticated = useAuthStore((s) => s.isAuthenticated);
  const role = useAuthStore((s) => s.role);

  if (!isAuthenticated) {
    const target = encodeURIComponent(location.pathname + location.search);
    return <Navigate to={`/login?redirect_to=${target}`} replace />;
  }
  if (role !== 'ADMIN') {
    return <Navigate to="/" replace />;
  }
  return <Outlet />;
}
