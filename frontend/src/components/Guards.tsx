import type { ReactNode } from "react";
import { Navigate, useLocation } from "react-router-dom";
import { useAuth } from "../context/AuthContext";
import { Loading } from "./Bits";

export function RequireAuth({ children }: { children: ReactNode }) {
  const { isAuthenticated, loading } = useAuth();
  const loc = useLocation();
  if (loading) return <Loading />;
  if (!isAuthenticated) return <Navigate to="/login" state={{ from: loc.pathname + loc.search }} replace />;
  return <>{children}</>;
}

export function RequireAdmin({ children }: { children: ReactNode }) {
  const { isAdmin, isAuthenticated, loading } = useAuth();
  const loc = useLocation();
  if (loading) return <Loading />;
  if (!isAuthenticated) return <Navigate to="/login" state={{ from: loc.pathname }} replace />;
  if (!isAdmin)
    return (
      <div className="container page">
        <div className="empty">
          <h3>Admins only</h3>
          <p className="muted">This area needs the ADMIN role.</p>
        </div>
      </div>
    );
  return <>{children}</>;
}
