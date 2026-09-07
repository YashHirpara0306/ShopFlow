import { useState } from "react";
import { useMutation, useQuery, useQueryClient } from "@tanstack/react-query";
import { listCategories, listProducts } from "../../api/catalog";
import { createCategory, createProduct } from "../../api/admin";
import { errorMessage } from "../../api/client";
import { useToast } from "../../context/ToastContext";
import { money } from "../../lib/format";
import { Loading, Spinner } from "../../components/Bits";

export function AdminProducts() {
  const qc = useQueryClient();
  const toast = useToast();
  const { data: categories } = useQuery({ queryKey: ["categories"], queryFn: listCategories });
  const { data: products, isLoading } = useQuery({
    queryKey: ["products", { size: 50, sort: "createdAt,desc" }],
    queryFn: () => listProducts({ size: 50, sort: "createdAt,desc" }),
  });

  const [newCat, setNewCat] = useState("");
  const [form, setForm] = useState({ sku: "", name: "", description: "", price: "", categoryId: "", imageUrl: "" });

  const addCategory = useMutation({
    mutationFn: () => createCategory(newCat.trim()),
    onSuccess: (c) => {
      toast(`Category “${c.name}” created`, "ok");
      setNewCat("");
      qc.invalidateQueries({ queryKey: ["categories"] });
    },
    onError: (e) => toast(errorMessage(e), "err"),
  });

  const addProduct = useMutation({
    mutationFn: () =>
      createProduct({
        sku: form.sku.trim(),
        name: form.name.trim(),
        description: form.description.trim() || undefined,
        price: Number(form.price),
        categoryId: form.categoryId,
        imageUrl: form.imageUrl.trim() || undefined,
      }),
    onSuccess: (p) => {
      toast(`Product “${p.name}” created`, "ok");
      setForm({ sku: "", name: "", description: "", price: "", categoryId: "", imageUrl: "" });
      qc.invalidateQueries({ queryKey: ["products"] });
    },
    onError: (e) => toast(errorMessage(e), "err"),
  });

  const set = (k: keyof typeof form) => (e: React.ChangeEvent<HTMLInputElement | HTMLSelectElement | HTMLTextAreaElement>) =>
    setForm((f) => ({ ...f, [k]: e.target.value }));

  return (
    <div className="split split--narrow">
      <div>
        <div className="card" style={{ padding: 22, marginBottom: 20 }}>
          <div className="eyebrow" style={{ marginBottom: 12 }}>
            New product
          </div>
          <form
            className="stack"
            onSubmit={(e) => {
              e.preventDefault();
              addProduct.mutate();
            }}
          >
            <div className="grid2">
              <label className="field">
                <span>SKU</span>
                <input required value={form.sku} onChange={set("sku")} />
              </label>
              <label className="field">
                <span>Price</span>
                <input required type="number" step="0.01" min="0.01" value={form.price} onChange={set("price")} />
              </label>
            </div>
            <label className="field">
              <span>Name</span>
              <input required value={form.name} onChange={set("name")} />
            </label>
            <label className="field">
              <span>Description</span>
              <textarea rows={3} value={form.description} onChange={set("description")} />
            </label>
            <div className="grid2">
              <label className="field">
                <span>Category</span>
                <select required value={form.categoryId} onChange={set("categoryId")}>
                  <option value="">Choose…</option>
                  {categories?.map((c) => (
                    <option key={c.id} value={c.id}>
                      {c.name}
                    </option>
                  ))}
                </select>
              </label>
              <label className="field">
                <span>Image URL (optional)</span>
                <input value={form.imageUrl} onChange={set("imageUrl")} placeholder="https://…" />
              </label>
            </div>
            <button className="btn" disabled={addProduct.isPending}>
              {addProduct.isPending ? <Spinner /> : "Create product"}
            </button>
          </form>
        </div>

        <div className="card">
          {isLoading ? (
            <Loading />
          ) : (
            (products?.content ?? []).map((p, i) => (
              <div
                key={p.id}
                className="lineitem"
                style={{ padding: "14px 20px", gridTemplateColumns: "1fr auto", borderBottom: i === (products!.content.length - 1) ? "0" : undefined }}
              >
                <div>
                  <div style={{ fontWeight: 600 }}>{p.name}</div>
                  <div className="muted" style={{ fontSize: ".78rem" }}>
                    {p.sku} · {p.categoryName} · {p.active ? "active" : "inactive"}
                  </div>
                </div>
                <div>{money(p.price, p.currency)}</div>
              </div>
            ))
          )}
        </div>
      </div>

      <div className="card summary">
        <div className="eyebrow" style={{ marginBottom: 12 }}>
          New category
        </div>
        <form
          className="stack"
          onSubmit={(e) => {
            e.preventDefault();
            addCategory.mutate();
          }}
        >
          <label className="field">
            <span>Name</span>
            <input required value={newCat} onChange={(e) => setNewCat(e.target.value)} />
          </label>
          <button className="btn btn--ghost btn--block" disabled={addCategory.isPending}>
            {addCategory.isPending ? <Spinner /> : "Add category"}
          </button>
        </form>
        <hr className="divider" style={{ margin: "16px 0" }} />
        <div className="muted" style={{ fontSize: ".8rem" }}>
          Creating a product also calls Inventory to create a stock row at 0 — set a quantity on the Inventory tab.
        </div>
      </div>
    </div>
  );
}
