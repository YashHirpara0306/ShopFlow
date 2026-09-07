import { Link } from "react-router-dom";
import { useQuery } from "@tanstack/react-query";
import { listMyOrders } from "../api/catalog";
import { Loading, StatusBadge } from "../components/Bits";
import { money, timeAgo } from "../lib/format";

export function Orders() {
  const { data, isLoading } = useQuery({ queryKey: ["my-orders"], queryFn: () => listMyOrders(0, 50) });

  if (isLoading) return <div className="container page"><Loading /></div>;

  return (
    <div className="container page">
      <div className="page__head">
        <h1>My orders</h1>
      </div>
      {!data || data.content.length === 0 ? (
        <div className="empty">
          <h3>No orders yet</h3>
          <Link to="/products" className="btn">
            Browse products
          </Link>
        </div>
      ) : (
        <div className="card">
          {data.content.map((o, i) => (
            <Link
              key={o.id}
              to={`/orders/${o.id}`}
              className="lineitem"
              style={{ padding: "18px 22px", gridTemplateColumns: "1fr auto auto", borderBottom: i === data.content.length - 1 ? "0" : undefined }}
            >
              <div>
                <div style={{ fontWeight: 600 }}>#{o.id.slice(0, 8)}</div>
                <div className="muted" style={{ fontSize: ".8rem" }}>
                  {timeAgo(o.createdAt)} · {o.items.reduce((n, x) => n + x.quantity, 0)} item(s)
                </div>
              </div>
              <div style={{ alignSelf: "center" }}>{money(o.totalAmount, o.currency)}</div>
              <div style={{ alignSelf: "center" }}>
                <StatusBadge status={o.status} />
              </div>
            </Link>
          ))}
        </div>
      )}
    </div>
  );
}
