import { useEffect } from "react";
import type { Product } from "../api/types";
import { money } from "../lib/format";
import { IconClose } from "./Icons";
import { ProductMedia } from "./ProductMedia";

export function QuickView({
  product,
  onClose,
  onAdd,
}: {
  product: Product | null;
  onClose: () => void;
  onAdd: (p: Product) => void;
}) {
  useEffect(() => {
    if (!product) return;
    const onKey = (e: KeyboardEvent) => e.key === "Escape" && onClose();
    document.addEventListener("keydown", onKey);
    document.body.style.overflow = "hidden";
    return () => {
      document.removeEventListener("keydown", onKey);
      document.body.style.overflow = "";
    };
  }, [product, onClose]);

  if (!product) return null;

  return (
    <div className="overlay" onClick={onClose}>
      <div className="modal" style={{ position: "relative" }} onClick={(e) => e.stopPropagation()} role="dialog" aria-modal>
        <button className="modal__close" onClick={onClose} aria-label="Close">
          <IconClose />
        </button>
        <div className="quick">
          <div className="quick__media">
            <ProductMedia product={product} />
          </div>
          <div className="pcard__cat">{product.categoryName}</div>
          <h3>{product.name}</h3>
          <p>
            {product.description ||
              "Quality photo printing from a wide range of sizes and finishes to suit all your needs."}
          </p>
          <div className="quick__foot">
            <span className="quick__price">
              <b>{money(product.price, product.currency)}</b>
            </span>
            <div style={{ display: "flex", gap: 18, alignItems: "center" }}>
              <button className="link-teal" onClick={onClose}>
                Skip
              </button>
              <button
                className="btn"
                onClick={() => {
                  onAdd(product);
                  onClose();
                }}
              >
                Add to cart
              </button>
            </div>
          </div>
        </div>
      </div>
    </div>
  );
}
