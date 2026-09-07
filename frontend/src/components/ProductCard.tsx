import { Link } from "react-router-dom";
import type { Product } from "../api/types";
import { Price } from "./Bits";
import { ProductMedia } from "./ProductMedia";

export function ProductCard({ product, onQuickView }: { product: Product; onQuickView?: (p: Product) => void }) {
  return (
    <article className="pcard">
      <Link to={`/products/${product.id}`} className="pcard__media">
        <ProductMedia product={product} />
        {onQuickView && (
          <button
            type="button"
            className="btn btn--sm btn--block pcard__quick"
            onClick={(e) => {
              e.preventDefault();
              onQuickView(product);
            }}
          >
            Quick view
          </button>
        )}
      </Link>
      <div>
        <div className="pcard__cat" style={{ marginTop: 12 }}>
          {product.categoryName}
        </div>
        <div className="pcard__body">
          <Link to={`/products/${product.id}`} className="pcard__name">
            {product.name}
          </Link>
          <Price value={product.price} currency={product.currency} from />
        </div>
      </div>
    </article>
  );
}
