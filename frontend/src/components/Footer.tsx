import { Link } from "react-router-dom";

export function Footer() {
  return (
    <footer className="ftr">
      <div className="container ftr__grid">
        <div>
          <div className="ftr__brand">Rebt</div>
          <p style={{ maxWidth: "32ch", color: "#9aa09e", marginTop: 14, fontSize: ".85rem" }}>
            Quality, customizable photo products — printed on demand and delivered to your door.
          </p>
        </div>
        <div>
          <h4>Shop</h4>
          <Link to="/products">All products</Link>
          <Link to="/products?category=books">Photo books</Link>
          <Link to="/products?category=prints">Prints</Link>
          <Link to="/cart">Cart</Link>
        </div>
        <div>
          <h4>Company</h4>
          <a href="#">About</a>
          <a href="#">Sustainability</a>
          <a href="#">Careers</a>
        </div>
        <div>
          <h4>Help</h4>
          <Link to="/track">Track your order</Link>
          <a href="#">Shipping</a>
          <a href="#">Returns</a>
          <a href="#">Contact</a>
        </div>
      </div>
      <div className="container ftr__bar">
        <span>© {new Date().getFullYear()} Rebt. A ShopFlow demo storefront.</span>
        <span>Viridian · Desert Sand · Onyx</span>
      </div>
    </footer>
  );
}
