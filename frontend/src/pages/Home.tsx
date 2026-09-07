import { useState } from "react";
import { Link } from "react-router-dom";
import { useQuery } from "@tanstack/react-query";
import { listProducts } from "../api/catalog";
import type { Product } from "../api/types";
import { ProductCard } from "../components/ProductCard";
import { QuickView } from "../components/QuickView";
import { Loading } from "../components/Bits";
import { IconHeadset, IconSliders, IconSpark } from "../components/Icons";
import { useCart } from "../context/CartContext";
import { useToast } from "../context/ToastContext";

export function Home() {
  const { add } = useCart();
  const toast = useToast();
  const [quick, setQuick] = useState<Product | null>(null);

  const { data, isLoading } = useQuery({
    queryKey: ["products", { size: 6, sort: "price,asc" }],
    queryFn: () => listProducts({ size: 6, sort: "price,asc" }),
  });

  const addToCart = (p: Product) => {
    add(p);
    toast(`Added “${p.name}” to your cart`, "ok");
  };

  return (
    <>
      {/* hero */}
      <section className="hero">
        <div className="container hero__grid">
          <div>
            <span className="eyebrow eyebrow--teal">#SaveTheBest</span>
            <h1>
              Save memories
              <br />
              with us
            </h1>
            <p>Beautiful, themed photo products — printed on demand, completely customizable, delivered to your door.</p>
            <Link to="/products" className="btn">
              Shop the range
            </Link>
          </div>
          <div className="hero__art">
            <div className="frame" />
            <div className="plate">
              <img
                src="https://images.unsplash.com/photo-1493934558415-9d19f0b2b4d2?auto=format&fit=crop&w=900&q=70"
                alt="A photo book on a table"
                onError={(e) => ((e.currentTarget.style.display = "none"))}
              />
            </div>
            <span className="chip">The Feminine — from a curated theme</span>
          </div>
        </div>
      </section>

      {/* feature strip */}
      <div className="container features">
        <div className="features__card">
          <div className="feature">
            <IconSpark />
            <div>
              <b>Quality material</b>
              <span>Archival inks and papers on every product.</span>
            </div>
          </div>
          <div className="feature">
            <IconSliders />
            <div>
              <b>Completely customizable</b>
              <span>Sizes, finishes and layouts to suit any project.</span>
            </div>
          </div>
          <div className="feature">
            <IconHeadset />
            <div>
              <b>24/7 customer support</b>
              <span>Real people, whenever you need a hand.</span>
            </div>
          </div>
        </div>
      </div>

      {/* best products */}
      <section className="section container">
        <div className="section__head section__head--row">
          <div>
            <span className="eyebrow">Best sellers</span>
            <h2>Best products</h2>
          </div>
          <Link to="/products" className="link-teal">
            View all
          </Link>
        </div>

        {isLoading ? (
          <Loading label="Loading products" />
        ) : !data || data.content.length === 0 ? (
          <div className="empty">
            <h3>No products yet</h3>
            <p className="muted">
              Sign in as an admin and add a few from <Link to="/admin">the admin area</Link>, or run{" "}
              <code>scripts/smoke.sh</code>.
            </p>
          </div>
        ) : (
          <div className="pgrid">
            {data.content.map((p) => (
              <ProductCard key={p.id} product={p} onQuickView={setQuick} />
            ))}
          </div>
        )}
      </section>

      <QuickView product={quick} onClose={() => setQuick(null)} onAdd={addToCart} />
    </>
  );
}
