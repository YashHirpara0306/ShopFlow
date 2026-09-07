import { Link, useParams } from "react-router-dom";
import { useQuery } from "@tanstack/react-query";
import { getOrder } from "../api/catalog";
import type { Order } from "../api/types";
import { Loading, StatusBadge, statusLabel } from "../components/Bits";
import { money, timeAgo } from "../lib/format";

const TERMINAL = ["CONFIRMED", "CANCELLED", "REFUNDED", "FAILED"];

export function OrderStatus() {
  const { id = "" } = useParams();

  const { data: order, isLoading, isError } = useQuery({
    queryKey: ["order", id],
    queryFn: () => getOrder(id),
    refetchInterval: (q) => {
      const s = (q.state.data as Order | undefined)?.status;
      return s && TERMINAL.includes(s) ? false : 2000;
    },
  });

  if (isLoading) return <div className="container page"><Loading label="Fetching your order" /></div>;
  if (isError || !order)
    return (
      <div className="container page">
        <div className="empty">
          <h3>Order not found</h3>
          <p className="muted">Check the id, or sign in with the account that placed it.</p>
        </div>
      </div>
    );

  const live = !TERMINAL.includes(order.status);

  return (
    <div className="container page">
      <div className="crumbs">
        <Link to="/orders">My orders</Link> · #{order.id.slice(0, 8)}
      </div>
      <div className="split split--narrow" style={{ marginTop: 16 }}>
        <div>
          <div style={{ display: "flex", alignItems: "center", gap: 14 }}>
            <h1 style={{ fontSize: "2rem" }}>Order {live ? "in progress" : statusLabel(order.status)}</h1>
            <StatusBadge status={order.status} />
          </div>
          <p className="muted" style={{ fontSize: ".85rem" }}>
            Placed {timeAgo(order.createdAt)} · {order.items.reduce((n, i) => n + i.quantity, 0)} item(s) ·{" "}
            {money(order.totalAmount, order.currency)}
            {order.cancelReason && ` · reason: ${order.cancelReason}`}
          </p>

          <ol className="tl">
            {order.history.map((h, i) => (
              <li key={i} className={i < order.history.length - 1 || !live ? "done" : ""}>
                <b>{statusLabel(h.toStatus)}</b>
                <span>
                  {h.reason ? `${h.reason} · ` : ""}
                  {timeAgo(h.at)}
                </span>
              </li>
            ))}
            {live && (
              <li>
                <b style={{ color: "var(--ink-faint)" }}>…working</b>
                <span>auto-refreshing every 2s</span>
              </li>
            )}
          </ol>
        </div>

        <div className="card summary">
          <div className="eyebrow" style={{ marginBottom: 12 }}>
            Items
          </div>
          {order.items.map((i) => (
            <div className="summary__row" key={i.productId}>
              <span>
                {i.productName} × {i.quantity}
              </span>
              <span>{money(i.lineTotal, order.currency)}</span>
            </div>
          ))}
          <div className="summary__row summary__row--total">
            <span>Total</span>
            <span>{money(order.totalAmount, order.currency)}</span>
          </div>
          <Link to="/products" className="btn btn--ghost btn--block" style={{ marginTop: 16 }}>
            Keep shopping
          </Link>
        </div>
      </div>
    </div>
  );
}
