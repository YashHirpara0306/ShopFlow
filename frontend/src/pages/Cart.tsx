import { Link, useNavigate } from "react-router-dom";
import { useCart } from "../context/CartContext";
import { money } from "../lib/format";

export function Cart() {
  const { lines, setQty, remove, subtotal, currency, count } = useCart();
  const nav = useNavigate();

  if (count === 0)
    return (
      <div className="container page">
        <div className="empty">
          <h3>Your cart is empty</h3>
          <p className="muted" style={{ marginBottom: 18 }}>
            Add a few photo products to get started.
          </p>
          <Link to="/products" className="btn">
            Browse products
          </Link>
        </div>
      </div>
    );

  return (
    <div className="container page">
      <div className="page__head">
        <h1>Your cart</h1>
      </div>
      <div className="split split--narrow">
        <div>
          {lines.map((l) => (
            <div className="lineitem" key={l.productId}>
              <div className="lineitem__thumb">
                {l.imageUrl ? <img src={l.imageUrl} alt="" /> : l.name.charAt(0).toUpperCase()}
              </div>
              <div>
                <Link to={`/products/${l.productId}`} style={{ fontFamily: "var(--font-serif)", fontSize: "1.02rem" }}>
                  {l.name}
                </Link>
                <div className="muted" style={{ fontSize: ".82rem" }}>
                  {money(l.price, l.currency)} each
                </div>
                <div style={{ display: "flex", gap: 14, alignItems: "center", marginTop: 8 }}>
                  <div className="qty">
                    <button onClick={() => setQty(l.productId, l.quantity - 1)}>–</button>
                    <span>{l.quantity}</span>
                    <button onClick={() => setQty(l.productId, l.quantity + 1)}>+</button>
                  </div>
                  <button className="link-teal" onClick={() => remove(l.productId)}>
                    Remove
                  </button>
                </div>
              </div>
              <div style={{ fontWeight: 600 }}>{money(l.price * l.quantity, l.currency)}</div>
            </div>
          ))}
        </div>

        <div className="card summary">
          <div className="eyebrow" style={{ marginBottom: 12 }}>
            Order summary
          </div>
          <div className="summary__row">
            <span>Items ({count})</span>
            <span>{money(subtotal, currency)}</span>
          </div>
          <div className="summary__row">
            <span>Shipping</span>
            <span className="muted">Calculated at checkout</span>
          </div>
          <div className="summary__row summary__row--total">
            <span>Subtotal</span>
            <span>{money(subtotal, currency)}</span>
          </div>
          <button className="btn btn--block" style={{ marginTop: 16 }} onClick={() => nav("/checkout")}>
            Checkout
          </button>
          <Link to="/products" className="link-teal" style={{ display: "block", textAlign: "center", marginTop: 14 }}>
            Continue shopping
          </Link>
        </div>
      </div>
    </div>
  );
}
