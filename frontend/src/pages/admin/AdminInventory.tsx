import { useState } from "react";
import { useMutation, useQuery } from "@tanstack/react-query";
import { listProducts } from "../../api/catalog";
import { getStock, setStock } from "../../api/admin";
import { errorMessage } from "../../api/client";
import { useToast } from "../../context/ToastContext";
import { Loading, Spinner } from "../../components/Bits";

export function AdminInventory() {
  const toast = useToast();
  const { data: products, isLoading } = useQuery({
    queryKey: ["products", { size: 100, sort: "name,asc" }],
    queryFn: () => listProducts({ size: 100, sort: "name,asc" }),
  });

  const [selected, setSelected] = useState("");
  const [qty, setQty] = useState("");

  const stock = useQuery({
    queryKey: ["stock", selected],
    queryFn: () => getStock(selected),
    enabled: !!selected,
  });

  const save = useMutation({
    mutationFn: () => setStock(selected, Number(qty)),
    onSuccess: (s) => {
      toast(`Stock set — available ${s.available}`, "ok");
      stock.refetch();
    },
    onError: (e) => toast(errorMessage(e), "err"),
  });

  if (isLoading) return <Loading />;

  return (
    <div className="split split--narrow">
      <div className="card" style={{ padding: 22 }}>
        <div className="eyebrow" style={{ marginBottom: 12 }}>
          Set on-hand quantity
        </div>
        <form
          className="stack"
          onSubmit={(e) => {
            e.preventDefault();
            save.mutate();
          }}
        >
          <label className="field">
            <span>Product</span>
            <select required value={selected} onChange={(e) => setSelected(e.target.value)}>
              <option value="">Choose…</option>
              {products?.content.map((p) => (
                <option key={p.id} value={p.id}>
                  {p.name} — {p.sku}
                </option>
              ))}
            </select>
          </label>
          <label className="field">
            <span>Quantity</span>
            <input required type="number" min="0" value={qty} onChange={(e) => setQty(e.target.value)} />
          </label>
          <button className="btn" disabled={!selected || save.isPending}>
            {save.isPending ? <Spinner /> : "Set stock"}
          </button>
        </form>
      </div>

      <div className="card summary">
        <div className="eyebrow" style={{ marginBottom: 12 }}>
          Current stock
        </div>
        {!selected ? (
          <p className="muted" style={{ fontSize: ".85rem" }}>
            Pick a product to see its stock.
          </p>
        ) : stock.isLoading ? (
          <Spinner />
        ) : stock.isError ? (
          <p className="muted" style={{ fontSize: ".85rem" }}>
            No stock row yet — set a quantity.
          </p>
        ) : (
          <dl style={{ display: "grid", gridTemplateColumns: "auto 1fr", gap: "8px 20px", fontSize: ".9rem" }}>
            <dt className="muted">Total</dt>
            <dd>{stock.data!.total}</dd>
            <dt className="muted">Available</dt>
            <dd>{stock.data!.available}</dd>
            <dt className="muted">Reserved</dt>
            <dd>{stock.data!.reserved}</dd>
          </dl>
        )}
      </div>
    </div>
  );
}
