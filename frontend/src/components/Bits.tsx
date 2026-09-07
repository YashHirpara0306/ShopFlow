import type { ReactNode } from "react";
import { money } from "../lib/format";
import type { OrderStatus } from "../api/types";

export function Spinner() {
  return <span className="spinner" aria-label="Loading" />;
}

export function Loading({ label = "Loading" }: { label?: string }) {
  return (
    <div className="empty">
      <Spinner />
      <p className="muted" style={{ marginTop: 12 }}>
        {label}…
      </p>
    </div>
  );
}

export function Empty({ title, children }: { title: string; children?: ReactNode }) {
  return (
    <div className="empty">
      <h3>{title}</h3>
      {children}
    </div>
  );
}

export function Price({ value, currency, from }: { value: number; currency: string; from?: boolean }) {
  return (
    <span className="price">
      {from && <small>From</small>}
      <b>{money(value, currency)}</b>
    </span>
  );
}

const STATUS_LABEL: Record<OrderStatus, string> = {
  PENDING: "Placed",
  INVENTORY_PENDING: "Reserving stock",
  AWAITING_PAYMENT: "Taking payment",
  CONFIRMED: "Confirmed",
  CANCELLED: "Cancelled",
  REFUNDED: "Refunded",
  FAILED: "Failed",
};

export function StatusBadge({ status }: { status: OrderStatus }) {
  const cls =
    status === "CONFIRMED"
      ? "badge--teal"
      : status === "CANCELLED" || status === "FAILED"
        ? "badge--bad"
        : status === "REFUNDED"
          ? "badge--warn"
          : "";
  return <span className={`badge ${cls}`}>{STATUS_LABEL[status] ?? status}</span>;
}

export function statusLabel(s: string) {
  return STATUS_LABEL[s as OrderStatus] ?? s;
}
