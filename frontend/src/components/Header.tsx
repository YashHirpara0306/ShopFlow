import { useEffect, useRef, useState } from "react";
import { Link, NavLink, useNavigate } from "react-router-dom";
import { useAuth } from "../context/AuthContext";
import { useCart } from "../context/CartContext";
import { IconBag, IconChevron, IconSearch, IconUser } from "./Icons";

export function Header() {
  const { count } = useCart();
  const { user, isAdmin, signOut } = useAuth();
  const nav = useNavigate();
  const [searchOpen, setSearchOpen] = useState(false);
  const [menuOpen, setMenuOpen] = useState(false);
  const [term, setTerm] = useState("");
  const menuRef = useRef<HTMLDivElement>(null);

  useEffect(() => {
    const onDoc = (e: MouseEvent) => {
      if (menuRef.current && !menuRef.current.contains(e.target as Node)) setMenuOpen(false);
    };
    document.addEventListener("mousedown", onDoc);
    return () => document.removeEventListener("mousedown", onDoc);
  }, []);

  const submitSearch = (e: React.FormEvent) => {
    e.preventDefault();
    setSearchOpen(false);
    nav(`/products?q=${encodeURIComponent(term.trim())}`);
  };

  return (
    <header className="hdr">
      <div className="container hdr__row">
        <Link to="/" className="hdr__brand">
          Rebt
        </Link>

        <nav className="hdr__nav">
          <NavLink to="/products" className={({ isActive }) => (isActive ? "is-active" : "")}>
            Products
          </NavLink>
          <NavLink to="/track" className={({ isActive }) => (isActive ? "is-active" : "")}>
            Track your order
          </NavLink>
        </nav>

        <div className="hdr__actions">
          <button className="iconbtn" aria-label="Search" onClick={() => setSearchOpen((v) => !v)}>
            <IconSearch />
          </button>

          <Link to="/cart" className="iconbtn" aria-label={`Cart, ${count} items`}>
            <IconBag />
            {count > 0 && <span className="iconbtn__count">{count}</span>}
          </Link>

          <div ref={menuRef} style={{ position: "relative" }}>
            <button className="iconbtn" aria-label="Account" onClick={() => setMenuOpen((v) => !v)}>
              <IconUser />
              <IconChevron size={12} />
            </button>
            {menuOpen && (
              <div className="menu">
                {user ? (
                  <>
                    <div className="menu__label">{user.displayName}</div>
                    <Link to="/account" onClick={() => setMenuOpen(false)}>
                      Account
                    </Link>
                    <Link to="/orders" onClick={() => setMenuOpen(false)}>
                      My orders
                    </Link>
                    {isAdmin && (
                      <Link to="/admin" onClick={() => setMenuOpen(false)}>
                        Admin
                      </Link>
                    )}
                    <hr className="divider" style={{ margin: "6px 0" }} />
                    <button
                      onClick={async () => {
                        setMenuOpen(false);
                        await signOut();
                        nav("/");
                      }}
                    >
                      Sign out
                    </button>
                  </>
                ) : (
                  <>
                    <Link to="/login" onClick={() => setMenuOpen(false)}>
                      Sign in
                    </Link>
                    <Link to="/register" onClick={() => setMenuOpen(false)}>
                      Create account
                    </Link>
                  </>
                )}
              </div>
            )}
          </div>
        </div>
      </div>

      {searchOpen && (
        <div className="hdr__search">
          <form className="container" onSubmit={submitSearch}>
            <input
              autoFocus
              placeholder="Search products…"
              value={term}
              onChange={(e) => setTerm(e.target.value)}
              onBlur={() => !term && setSearchOpen(false)}
            />
          </form>
        </div>
      )}
    </header>
  );
}
