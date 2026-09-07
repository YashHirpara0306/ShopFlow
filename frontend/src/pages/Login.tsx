import { useState } from "react";
import { Link, useLocation, useNavigate } from "react-router-dom";
import { errorMessage } from "../api/client";
import { useAuth } from "../context/AuthContext";
import { Spinner } from "../components/Bits";

export function Login() {
  const { signIn } = useAuth();
  const nav = useNavigate();
  const loc = useLocation() as { state?: { from?: string } };
  const [email, setEmail] = useState("");
  const [password, setPassword] = useState("");
  const [busy, setBusy] = useState(false);
  const [err, setErr] = useState<string | null>(null);

  const submit = async (e: React.FormEvent) => {
    e.preventDefault();
    setBusy(true);
    setErr(null);
    try {
      await signIn(email.trim(), password);
      nav(loc.state?.from || "/", { replace: true });
    } catch (e2) {
      setErr(errorMessage(e2));
      setBusy(false);
    }
  };

  return (
    <div className="container">
      <div className="authwrap">
        <div className="crumbs center">Account</div>
        <div className="card">
          <h1>Sign in</h1>
          <p className="muted" style={{ marginBottom: 22 }}>
            Welcome back.
          </p>
          <form className="stack" onSubmit={submit}>
            <label className="field">
              <span>Email</span>
              <input type="email" required value={email} onChange={(e) => setEmail(e.target.value)} autoComplete="email" />
            </label>
            <label className="field">
              <span>Password</span>
              <input
                type="password"
                required
                value={password}
                onChange={(e) => setPassword(e.target.value)}
                autoComplete="current-password"
              />
            </label>
            {err && <div className="badge badge--bad">{err}</div>}
            <button className="btn btn--block" disabled={busy}>
              {busy ? <Spinner /> : "Sign in"}
            </button>
          </form>
          <p className="muted center" style={{ marginTop: 18, fontSize: ".85rem" }}>
            New here? <Link to="/register" className="link-teal">Create an account</Link>
          </p>
        </div>
      </div>
    </div>
  );
}
