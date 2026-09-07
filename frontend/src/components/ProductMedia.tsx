import type { Product } from "../api/types";

/** Product image with a tinted-panel fallback (the DB often has no imageUrl). */
export function ProductMedia({ product, className }: { product: Pick<Product, "name" | "imageUrl">; className?: string }) {
  if (product.imageUrl) {
    return <img className={className} src={product.imageUrl} alt={product.name} loading="lazy" />;
  }
  return (
    <span className="pcard__ph" aria-hidden>
      {product.name.trim().charAt(0).toUpperCase() || "•"}
    </span>
  );
}
