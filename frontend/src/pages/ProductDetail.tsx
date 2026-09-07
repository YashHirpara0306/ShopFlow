import { useState } from "react";
import { Link, useNavigate, useParams } from "react-router-dom";
import { useQuery } from "@tanstack/react-query";
import { getProduct } from "../api/catalog";
import { Loading, Price } from "../components/Bits";
import { ProductMedia } from "../components/ProductMedia";
import { useCart } from "../context/CartContext";
import { useToast } from "../context/ToastContext";

export function ProductDetail() {
  const { id = "" } = useParams();
  const { add } = useCart();
  const toast = useToast();
  const nav = useNavigate();
  const [qty, setQty] = useState(1);

  const { data: product, isLoading, isError } = useQuery({
    queryKey: ["product", id],
    queryFn: () => getProduct(id),
  });

  if (isLoading) return <div className="container page"><Loading /></div>;
  if (isError || !product)
    return (
      <div className="container page">
        <div className="empty">
          <h3>Product not found</h3>
          <Link to="/products" className="link-teal">
            Back to products
          </Link>
        </div>
      </div>
    );

  return (
    <div className="container page">
      <div className="crumbs">
        <Link to="/">Home</Link> · <Link to="/products">Products</Link> · {product.name}
      </div>
      <div className="split pdetail" style={{ marginTop: 20 }}>
        <div className="pdetail__media">
          <ProductMedia product={product} />
        </div>
        <div>
          <div className="pcard__cat">{product.categoryName}</div>
          <h1>{product.name}</h1>
          <div style={{ margin: "6px 0 20px" }}>
            <Price value={product.price} currency={product.currency} />
          </div>
          <p className="muted" style={{ maxWidth: "48ch" }}>
            {product.description ||
              "From standard photo prints to posters, we have your printing needs covered — quality photo printing from a wide range of sizes and designs."}
          </p>

          <div style={{ display: "flex", gap: 14, alignItems: "center", margin: "26px 0" }}>
            <div className="qty">
              <button onClick={() => setQty((q) => Math.max(1, q - 1))} aria-label="Decrease">
                –
              </button>
              <span>{qty}</span>
              <button onClick={() => setQty((q) => Math.min(99, q + 1))} aria-label="Increase">
                +
              </button>
            </div>
            <button
              className="btn"
              onClick={() => {
                add(product, qty);
                toast(`Added ${qty} × “${product.name}”`, "ok");
              }}
            >
              Add to cart
            </button>
            <button
              className="btn btn--ghost"
              onClick={() => {
                add(product, qty);
                nav("/cart");
              }}
            >
              Buy now
            </button>
          </div>

          <hr className="divider" />
          <dl style={{ display: "grid", gridTemplateColumns: "auto 1fr", gap: "10px 20px", marginTop: 18, fontSize: ".85rem" }}>
            <dt className="muted">SKU</dt>
            <dd>{product.sku}</dd>
            <dt className="muted">Category</dt>
            <dd>{product.categoryName}</dd>
            <dt className="muted">Availability</dt>
            <dd>{product.active ? "In catalogue" : "Unavailable"}</dd>
          </dl>
        </div>
      </div>
    </div>
  );
}
