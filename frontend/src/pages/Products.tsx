import { useMemo, useState } from "react";
import { Link, useSearchParams } from "react-router-dom";
import { useQuery } from "@tanstack/react-query";
import { listCategories, listProducts } from "../api/catalog";
import type { Product } from "../api/types";
import { ProductCard } from "../components/ProductCard";
import { QuickView } from "../components/QuickView";
import { Loading } from "../components/Bits";
import { useCart } from "../context/CartContext";
import { useToast } from "../context/ToastContext";

const SIZE = 9;

export function Products() {
  const [params, setParams] = useSearchParams();
  const { add } = useCart();
  const toast = useToast();
  const [quick, setQuick] = useState<Product | null>(null);

  const q = params.get("q") ?? "";
  const category = params.get("category") ?? "";
  const sort = params.get("sort") ?? "createdAt,desc";
  const minPrice = params.get("minPrice") ?? "";
  const maxPrice = params.get("maxPrice") ?? "";
  const page = Number(params.get("page") ?? 0);

  const { data: categories } = useQuery({ queryKey: ["categories"], queryFn: listCategories });

  const query = useMemo(
    () => ({
      q: q || undefined,
      category: category || undefined,
      sort,
      minPrice: minPrice ? Number(minPrice) : undefined,
      maxPrice: maxPrice ? Number(maxPrice) : undefined,
      page,
      size: SIZE,
    }),
    [q, category, sort, minPrice, maxPrice, page],
  );

  const { data, isLoading } = useQuery({
    queryKey: ["products", query],
    queryFn: () => listProducts(query),
  });

  const patch = (next: Record<string, string>) => {
    const merged = new URLSearchParams(params);
    for (const [k, v] of Object.entries(next)) {
      if (v) merged.set(k, v);
      else merged.delete(k);
    }
    if (!("page" in next)) merged.delete("page");
    setParams(merged);
  };

  return (
    <div className="container page">
      <div className="page__head">
        <div className="crumbs">
          <Link to="/">Home</Link> · Products
        </div>
        <h1>{q ? `Results for “${q}”` : "All products"}</h1>
      </div>

      <div className="card filters">
        <label className="field grow">
          <span>Search</span>
          <input defaultValue={q} placeholder="e.g. photo book" onKeyDown={(e) => e.key === "Enter" && patch({ q: (e.target as HTMLInputElement).value })} />
        </label>
        <label className="field">
          <span>Category</span>
          <select value={category} onChange={(e) => patch({ category: e.target.value })}>
            <option value="">All</option>
            {categories?.map((c) => (
              <option key={c.id} value={c.slug}>
                {c.name}
              </option>
            ))}
          </select>
        </label>
        <label className="field">
          <span>Min price</span>
          <input type="number" min={0} defaultValue={minPrice} onBlur={(e) => patch({ minPrice: e.target.value })} />
        </label>
        <label className="field">
          <span>Max price</span>
          <input type="number" min={0} defaultValue={maxPrice} onBlur={(e) => patch({ maxPrice: e.target.value })} />
        </label>
        <label className="field">
          <span>Sort</span>
          <select value={sort} onChange={(e) => patch({ sort: e.target.value })}>
            <option value="createdAt,desc">Newest</option>
            <option value="price,asc">Price: low to high</option>
            <option value="price,desc">Price: high to low</option>
            <option value="name,asc">Name: A–Z</option>
          </select>
        </label>
      </div>

      {isLoading ? (
        <Loading label="Loading products" />
      ) : !data || data.content.length === 0 ? (
        <div className="empty">
          <h3>Nothing matched</h3>
          <p className="muted">Try a broader search or clear the filters.</p>
        </div>
      ) : (
        <>
          <div className="pgrid">
            {data.content.map((p) => (
              <ProductCard key={p.id} product={p} onQuickView={setQuick} />
            ))}
          </div>
          {data.totalPages > 1 && (
            <div className="pager">
              <button disabled={data.first} onClick={() => patch({ page: String(page - 1) })}>
                ‹
              </button>
              {Array.from({ length: data.totalPages }).map((_, i) => (
                <button key={i} className={i === page ? "is-active" : ""} onClick={() => patch({ page: String(i) })}>
                  {i + 1}
                </button>
              ))}
              <button disabled={data.last} onClick={() => patch({ page: String(page + 1) })}>
                ›
              </button>
            </div>
          )}
        </>
      )}

      <QuickView
        product={quick}
        onClose={() => setQuick(null)}
        onAdd={(p) => {
          add(p);
          toast(`Added “${p.name}” to your cart`, "ok");
        }}
      />
    </div>
  );
}
