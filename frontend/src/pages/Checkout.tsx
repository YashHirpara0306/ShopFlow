import { useState } from "react";
import { Link, useNavigate } from "react-router-dom";
import { createOrder, serverCartAdd, serverCartClear } from "../api/catalog";
import { errorMessage, uuid } from "../api/client";
import { useAuth } from "../context/AuthContext";
import { useCart } from "../context/CartContext";
import { useToast } from "../context/ToastContext";
import { money } from "../lib/format";
import { Spinner } from "../components/Bits";

export function Checkout() {
  const { lines, subtotal, currency, count, clear } = useCart();
  const { user } = useAuth();
  const toast = useToast();
  const nav = useNavigate();
  const [placing, setPlacing] = useState(false);

  if (count === 0)
    return (
      <div className="container page">
        <div className="empty">
          <h3>Nothing to check out</h3>
          <Link to="/products" className="btn">
            Browse products
          </Link>
        </div>
      </div>
    );

  const placeOrder = async () => {
    setPlacing(true);
    try {
      // stage the local cart onto the server cart, then let Order Service read it
      await serverCartClear();
      for (const l of lines) {
        await serverCartAdd(l.productId, l.quantity);
      }
      const order = await createOrder(uuid());
      clear();
      toast("Order placed — tracking it now", "ok");
      nav(`/orders/${order.id}`);
    } catch (e) {
      toast(errorMessage(e), "err");
      setPlacing(false);
    }
  };

  return (
    <div className="container page">
      <div className="page__head">
        <h1>Checkout</h1>
      </div>
      <div className="split split--narrow">
        <div>
          <div className="card" style={{ padding: 22 }}>
            <div className="eyebrow" style={{ marginBottom: 10 }}>
              Account
            </div>
            <p style={{ fontSize: ".9rem" }}>
              Signed in as <strong>{user?.email}</strong>.
            </p>
          </div>

          <div className="card" style={{ padding: 22, marginTop: 16 }}>
            <div className="eyebrow" style={{ marginBottom: 12 }}>
              Items
            </div>
            {lines.map((l) => (
              <div className="summary__row" key={l.productId}>
                <span>
                  {l.name} × {l.quantity}
                </span>
                <span>{money(l.price * l.quantity, l.currency)}</span>
              </div>
            ))}
          </div>

          <div className="card" style={{ padding: 22, marginTop: 16 }}>
            <div className="eyebrow" style={{ marginBottom: 10 }}>
              Payment
            </div>
            <p className="muted" style={{ fontSize: ".85rem" }}>
              This demo uses a <strong>simulated payment service</strong> — no card is collected. An amount whose
              cents are <code>.13</code> always declines (try it to see the Saga cancel &amp; release stock).
            </p>
          </div>
        </div>

        <div className="card summary">
          <div className="eyebrow" style={{ marginBottom: 12 }}>
            Total
          </div>
          <div className="summary__row">
            <span>Items ({count})</span>
            <span>{money(subtotal, currency)}</span>
          </div>
          <div className="summary__row">
            <span>Shipping</span>
            <span>Free</span>
          </div>
          <div className="summary__row summary__row--total">
            <span>To pay</span>
            <span>{money(subtotal, currency)}</span>
          </div>
          <button className="btn btn--block" style={{ marginTop: 16 }} disabled={placing} onClick={placeOrder}>
            {placing ? <Spinner /> : "Place order"}
          </button>
          <p className="muted" style={{ fontSize: ".75rem", textAlign: "center", marginTop: 12 }}>
            Order is accepted immediately (202); the Saga confirms it asynchronously.
          </p>
        </div>
      </div>
    </div>
  );
}
