import { Link } from "react-router-dom";
import { useAuth } from "../context/AuthContext";

export function Account() {
  const { user } = useAuth();
  if (!user) return null;

  return (
    <div className="container page">
      <div className="page__head">
        <h1>Account</h1>
      </div>
      <div className="split split--narrow">
        <div className="card" style={{ padding: 24 }}>
          <dl style={{ display: "grid", gridTemplateColumns: "auto 1fr", gap: "12px 24px", fontSize: ".9rem" }}>
            <dt className="muted">Name</dt>
            <dd>{user.displayName}</dd>
            <dt className="muted">Email</dt>
            <dd>{user.email}</dd>
            <dt className="muted">Roles</dt>
            <dd>{user.roles.join(", ")}</dd>
            <dt className="muted">Status</dt>
            <dd>{user.status}</dd>
            <dt className="muted">Member since</dt>
            <dd>{new Date(user.createdAt).toLocaleDateString()}</dd>
          </dl>
        </div>
        <div className="card summary">
          <div className="eyebrow" style={{ marginBottom: 12 }}>
            Quick links
          </div>
          <Link to="/orders" className="btn btn--ghost btn--block" style={{ marginBottom: 10 }}>
            My orders
          </Link>
          {user.roles.includes("ADMIN") && (
            <Link to="/admin" className="btn btn--ghost btn--block">
              Admin area
            </Link>
          )}
          <p className="muted" style={{ fontSize: ".75rem", marginTop: 12 }}>
            Profile editing is available on the API (<code>PUT /api/users/me</code>); not wired into this demo UI.
          </p>
        </div>
      </div>
    </div>
  );
}
