import { useState } from "react";
import { useNavigate } from "react-router-dom";

export function TrackOrder() {
  const [id, setId] = useState("");
  const nav = useNavigate();

  return (
    <div className="container page">
      <div className="authwrap">
        <div className="crumbs center">Support</div>
        <h1 className="center" style={{ marginBottom: 8 }}>
          Track your order
        </h1>
        <p className="muted center" style={{ marginBottom: 22 }}>
          Paste the order id you got at checkout.
        </p>
        <div className="card" style={{ padding: 28 }}>
          <form
            className="stack"
            onSubmit={(e) => {
              e.preventDefault();
              if (id.trim()) nav(`/orders/${id.trim()}`);
            }}
          >
            <label className="field">
              <span>Order id</span>
              <input value={id} onChange={(e) => setId(e.target.value)} placeholder="3f0d8b2a-…" />
            </label>
            <button className="btn btn--block" type="submit">
              Track
            </button>
          </form>
        </div>
      </div>
    </div>
  );
}
