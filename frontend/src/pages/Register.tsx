import { useState } from "react";
import { Link, useNavigate } from "react-router-dom";
import { errorMessage } from "../api/client";
import { useAuth } from "../context/AuthContext";
import { Spinner } from "../components/Bits";

export function Register() {
  const { signUp } = useAuth();
  const nav = useNavigate();
  const [form, setForm] = useState({ displayName: "", email: "", password: "" });
  const [busy, setBusy] = useState(false);
  const [err, setErr] = useState<string | null>(null);

  const set = (k: keyof typeof form) => (e: React.ChangeEvent<HTMLInputElement>) =>
    setForm((f) => ({ ...f, [k]: e.target.value }));

  const submit = async (e: React.FormEvent) => {
    e.preventDefault();
    setBusy(true);
    setErr(null);
    try {
      await signUp(form.email.trim(), form.password, form.displayName.trim());
      nav("/", { replace: true });
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
          <h1>Create account</h1>
          <p className="muted" style={{ marginBottom: 22 }}>
            It takes a few seconds.
          </p>
          <form className="stack" onSubmit={submit}>
            <label className="field">
              <span>Name</span>
              <input required value={form.displayName} onChange={set("displayName")} />
            </label>
            <label className="field">
              <span>Email</span>
              <input type="email" required value={form.email} onChange={set("email")} autoComplete="email" />
            </label>
            <label className="field">
              <span>Password</span>
              <input
                type="password"
                required
                minLength={8}
                value={form.password}
                onChange={set("password")}
                autoComplete="new-password"
              />
              <small className="muted" style={{ fontSize: ".72rem" }}>
                8–72 characters.
              </small>
            </label>
            {err && <div className="badge badge--bad">{err}</div>}
            <button className="btn btn--block" disabled={busy}>
              {busy ? <Spinner /> : "Create account"}
            </button>
          </form>
          <p className="muted center" style={{ marginTop: 18, fontSize: ".85rem" }}>
            Have an account? <Link to="/login" className="link-teal">Sign in</Link>
          </p>
        </div>
      </div>
    </div>
  );
}
